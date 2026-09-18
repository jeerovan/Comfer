package com.jeerovan.comfer.notes

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class NotesArchive(val version:Int=1,val encrypted:Boolean=false,val protectedContent:Boolean=false,
    val content:String="",val salt:String?=null,val nonce:String?=null,val sha256:String,val size:Long,
    val count:Int,val external:Boolean=false)
/** Recovery journal carries only existing device ciphertext, including private drafts. */
@Serializable data class NotesLocalRow(val id:String,val payload:String,val protected:Boolean=false,val revision:Long=0,val notebook:String="",val deletedAt:Long?=null,val noteId:String="")
@Serializable data class NotesLocalSnapshot(val notes:List<NotesLocalRow>,val drafts:List<NotesLocalRow>,val labels:List<NotesLocalRow>,val state:NotesLocalRow?,val generation:Long=0,val keyGeneration:String="")
object NotesBackup {
    const val ENTRY="notes/content.bin"
    const val MAX_BYTES=128L*1024*1024
    private val json=Json{encodeDefaults=true}
    private fun b64(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.NO_WRAP)
    private fun bytes(value:String)=Base64.decode(value,Base64.NO_WRAP)
    private fun hash(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString(""){"%02x".format(it)}
    private fun key(password:String,salt:ByteArray):SecretKeySpec {
        val spec=PBEKeySpec(password.toCharArray(),salt,1_300_000,256)
        return try{SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded,"AES")}finally{spec.clearPassword()}
    }
    fun pack(snapshot:NotesSnapshot,password:String?):Pair<NotesArchive,ByteArray> {
        snapshot.validate()
        val protected=snapshot.preferences.moduleLocked||snapshot.notes.any{it.protected}||snapshot.drafts.any{it.note.protected}
        require(!protected||password!=null){"Protected Notes require a backup password"}
        val source=json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        require(source.size<=MAX_BYTES){"Notes backup exceeds 128 MiB. Original notes are retained."}
        var salt:ByteArray?=null;var nonce:ByteArray?=null
        val data=if(password==null)source else {
            require(password.length>=4){"Password must contain at least 4 characters"}
            salt=ByteArray(16).also{SecureRandom().nextBytes(it)}
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt))
            nonce=cipher.iv;cipher.updateAAD("Comfer Notes archive 1".toByteArray());cipher.doFinal(source)
        }
        return NotesArchive(encrypted=password!=null,protectedContent=protected,salt=salt?.let(::b64),nonce=nonce?.let(::b64),sha256=hash(data),size=data.size.toLong(),count=snapshot.notes.size,external=true) to data
    }
    fun validate(archive:NotesArchive) {
        require(archive.version==1&&archive.size in 0..MAX_BYTES+16&&archive.count>=0&&archive.sha256.matches(Regex("[a-f0-9]{64}"))){"Invalid Notes archive"}
        require(!archive.protectedContent||archive.encrypted){"Protected Notes archive must be encrypted"}
        if(archive.encrypted)require(bytes(requireNotNull(archive.salt)).size==16&&bytes(requireNotNull(archive.nonce)).size==12)
        if(!archive.external)require(archive.content.length.toLong()<= (MAX_BYTES+16)*4/3+4)
    }
    fun unpack(archive:NotesArchive,password:String?,data:ByteArray?=null):NotesSnapshot {
        validate(archive)
        val encrypted=data?:bytes(archive.content)
        require(encrypted.size.toLong()==archive.size&&hash(encrypted)==archive.sha256){"Notes archive is damaged"}
        val decoded=if(!archive.encrypted)encrypted else {
            require(!password.isNullOrEmpty()){ "Enter the Notes export password" }
            val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(password,bytes(archive.salt!!)),GCMParameterSpec(128,bytes(archive.nonce!!)))
            c.updateAAD("Comfer Notes archive 1".toByteArray())
            try{c.doFinal(encrypted)}catch(_:java.security.GeneralSecurityException){throw IllegalArgumentException("Wrong Notes password or damaged archive")}
        }
        return json.decodeFromString<NotesSnapshot>(decoded.toString(Charsets.UTF_8)).also {
            it.validate();require(it.notes.size==archive.count)
            require(archive.encrypted||(!it.preferences.moduleLocked&&it.notes.none{n->n.protected}&&it.drafts.none{d->d.note.protected}))
        }
    }
    fun read(zip:ZipFile,archive:NotesArchive):ByteArray {
        validate(archive);val entry=zip.getEntry(ENTRY)?:error("Notes data is missing")
        require(entry.size==archive.size&&entry.size<=MAX_BYTES+16)
        val data=zip.getInputStream(entry).use{input->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0L
            while(true){val count=input.read(buffer);if(count<0)break;total+=count;require(total<=MAX_BYTES+16);out.write(buffer,0,count)};out.toByteArray()
        }
        require(hash(data)==archive.sha256){"Notes checksum failed"};return data
    }
    suspend fun requiresAuthentication(context:Context):Boolean {
        val dao=NotesDatabase.get(context).dao()
        if(dao.state()?.moduleLocked==true)return true
        var offset=0
        while(true){val rows=dao.page(offset=offset);if(rows.isEmpty())break;if(rows.any{it.protected})return true;offset+=rows.size}
        return dao.drafts().any{it.protected}
    }
    suspend fun snapshot(context:Context):NotesSnapshot {
        val store=NotesStore(NotesDatabase.get(context));store.initialize();return store.snapshot()
    }
    suspend fun local(context:Context):NotesLocalSnapshot=NotesDatabase.get(context).withTransaction {
        val dao=NotesDatabase.get(context).dao();val rows=mutableListOf<NoteRow>();var offset=0
        while(true){val page=dao.page(offset=offset);if(page.isEmpty())break;rows+=page;offset+=page.size}
        val state=dao.state()
        NotesLocalSnapshot(rows.map{NotesLocalRow(it.id,b64(it.payload),it.protected,it.revision,it.notebook,it.deletedAt)},
            dao.drafts().map{NotesLocalRow(it.id,b64(it.payload),it.protected,it.revision,noteId=it.noteId)},
            dao.labels().map{NotesLocalRow(it.id,b64(it.payload))},state?.let{NotesLocalRow("1",b64(it.payload),it.moduleLocked)},state?.generation?:0,state?.keyGeneration.orEmpty())
    }
    suspend fun restoreLocal(context:Context,snapshot:NotesLocalSnapshot)=NotesDatabase.get(context).withTransaction {
        val dao=NotesDatabase.get(context).dao();val generation=maxOf(snapshot.generation,dao.state()?.generation?:0)+1
        dao.clearNotes();dao.clearDrafts();dao.clearLabels();dao.clearState()
        snapshot.notes.forEach{dao.put(NoteRow(it.id,bytes(it.payload),it.protected,it.revision,it.notebook,it.deletedAt))}
        snapshot.drafts.forEach{dao.putDraft(NoteDraftRow(it.id,it.noteId,bytes(it.payload),it.protected,it.revision))}
        snapshot.labels.forEach{dao.putLabel(NoteLabelRow(it.id,bytes(it.payload)))}
        snapshot.state?.let{dao.putState(NotesStateRow(payload=bytes(it.payload),generation=generation,moduleLocked=it.protected,keyGeneration=snapshot.keyGeneration))}
    }
    /** Import defaults to keeping both conflicting versions, never replacing a newer edit. */
    fun merge(current:NotesSnapshot,incoming:NotesSnapshot):NotesSnapshot {
        current.validate();incoming.validate()
        if(current.notes.isEmpty() && current.drafts.isEmpty() && current.labels.all { it.id == Note.INBOX })
            return incoming.copy(preferences=incoming.preferences.copy(moduleLocked=current.preferences.moduleLocked||incoming.preferences.moduleLocked))
        val labels=current.labels.toMutableList();val labelIds=mutableMapOf<String,String>()
        incoming.labels.forEach{label->
            val match=labels.firstOrNull{it.id==label.id}
            val target=if(match==null||match==label||label.id==Note.INBOX)label.id else UUID.randomUUID().toString()
            labelIds[label.id]=target
            if(labels.none{it.id==target})labels+=label.copy(id=target)
        }
        val result=current.notes.toMutableList();val remap=mutableMapOf<String,String>()
        incoming.notes.forEach{original->
            val note=original.copy(notebook=labelIds.getValue(original.notebook),tags=original.tags.map{labelIds.getValue(it)}.toSet())
            val old=result.firstOrNull{it.id==note.id}
            val id=if(old==null||old==note)note.id else UUID.randomUUID().toString()
            remap[note.id]=id;if(result.none{it.id==id})result+=note.copy(id=id)
        }
        val drafts=current.drafts.toMutableList()
        incoming.drafts.forEach{d->
            val mapped=d.copy(note=d.note.copy(id=remap[d.note.id]?:d.note.id,notebook=labelIds.getValue(d.note.notebook),tags=d.note.tags.map{labelIds.getValue(it)}.toSet()))
            if(mapped !in drafts)drafts+=mapped.copy(id=if(drafts.any{it.id==mapped.id})UUID.randomUUID().toString() else mapped.id)
        }
        return current.copy(notes=result,drafts=drafts,labels=labels,preferences=current.preferences.copy(moduleLocked=current.preferences.moduleLocked||incoming.preferences.moduleLocked))
    }
    suspend fun restore(context:Context,snapshot:NotesSnapshot,replace:Boolean=false) {
        val store=NotesStore(NotesDatabase.get(context));store.initialize()
        store.db.withTransaction{store.replace(if(replace)snapshot else merge(store.snapshot(),snapshot),rotateKey=replace)}
    }
}
