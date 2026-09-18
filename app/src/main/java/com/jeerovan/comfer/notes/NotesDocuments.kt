package com.jeerovan.comfer.notes

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@Serializable data class NotesExportRecord(val at:Long,val count:Int,val destination:String,val result:String,val lastSuccessAt:Long?=if(result=="Completed")at else null)
object NotesDocuments {
    private val json=Json{encodeDefaults=true}
    suspend fun export(context:Context,uri:Uri,password:String?,selection:Set<String>?=null)=withContext(Dispatchers.IO){
        val all=NotesBackup.snapshot(context)
        val snapshot=if(selection==null)all else all.copy(notes=all.notes.filter{it.id in selection},drafts=emptyList())
        val (descriptor,data)=NotesBackup.pack(snapshot,password)
        try{
            val output=context.contentResolver.openOutputStream(uri,"w")?:error("Cannot open destination")
            ZipOutputStream(output.buffered()).use{zip->
                zip.putNextEntry(ZipEntry("notes.json"));zip.write(json.encodeToString(descriptor).toByteArray());zip.closeEntry()
                zip.putNextEntry(ZipEntry(NotesBackup.ENTRY));zip.write(data);zip.closeEntry()
            }
            val checked=read(context,uri,password)
            check(checked==snapshot){"Export verification failed"}
            record(context,NotesExportRecord(System.currentTimeMillis(),snapshot.notes.size,uri.toString(),"Completed"))
        }catch(e:Exception){record(context,NotesExportRecord(System.currentTimeMillis(),snapshot.notes.size,uri.toString(),"Failed"));throw e}
    }
    suspend fun read(context:Context,uri:Uri,password:String?):NotesSnapshot=withContext(Dispatchers.IO){
        val staged=File.createTempFile("notes-import-",".zip",context.cacheDir)
        try{
            (context.contentResolver.openInputStream(uri)?:error("Cannot open archive")).use{input->staged.outputStream().use{output->
                val buffer=ByteArray(8192);var total=0L
                while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=NotesBackup.MAX_BYTES+1024*1024){"Notes archive is too large"};output.write(buffer,0,n)}
            }}
            ZipFile(staged).use{zip->
                val entries=zip.entries().asSequence().toList()
                require(entries.size==2&&entries.map{it.name}.toSet()==setOf("notes.json",NotesBackup.ENTRY)&&entries.none{it.isDirectory}){"Unexpected Notes archive paths"}
                val metadata=zip.getEntry("notes.json");require(metadata.size in 1..16384)
                val descriptor=json.decodeFromString<NotesArchive>(zip.getInputStream(metadata).use{it.readBytes().toString(Charsets.UTF_8)})
                NotesBackup.unpack(descriptor,password,NotesBackup.read(zip,descriptor))
            }
        }finally{staged.delete()}
    }
    suspend fun text(context:Context,uri:Uri,notes:List<Note>)=withContext(Dispatchers.IO){
        if(notes.any{it.protected})NotesSession.requireUnlocked()
        val text=notes.joinToString("\n\n---\n\n"){it.content.title+"\n\n"+(if(it.content.checklist)it.content.asText().text else it.content.text)}
        (context.contentResolver.openOutputStream(uri,"w")?:error("Cannot open destination")).bufferedWriter(Charsets.UTF_8).use{it.write(text)}
    }
    suspend fun record(context:Context,value:NotesExportRecord){
        val file=AtomicFile(File(context.noBackupFilesDir,"notes-export-state"));val cipher=KeystoreNotesCipher().apply{selectGeneration(NotesDatabase.get(context).dao().state()?.keyGeneration.orEmpty())}
        val stored=if(value.result=="Completed")value else value.copy(lastSuccessAt=runCatching{lastExport(context)}.getOrNull()?.lastSuccessAt)
        val output=file.startWrite()
        try{output.write(cipher.seal(json.encodeToString(stored).toByteArray(),"Notes export state",false));file.finishWrite(output)}catch(e:Exception){file.failWrite(output);throw e}
    }
    fun lastExport(context:Context):NotesExportRecord?{
        val file=AtomicFile(File(context.noBackupFilesDir,"notes-export-state"))
        if(!file.baseFile.exists())return null
        return json.decodeFromString(KeystoreNotesCipher().open(file.readFully(),"Notes export state",false).toString(Charsets.UTF_8))
    }
}
