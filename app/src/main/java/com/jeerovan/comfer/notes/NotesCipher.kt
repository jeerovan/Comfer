package com.jeerovan.comfer.notes

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only ciphertext enters Room (including drafts and labels). AAD prevents row substitution. */
interface NotesCipher {
    fun selectGeneration(id:String) { }
    fun seal(bytes: ByteArray, identity: String, protected: Boolean): ByteArray
    fun open(bytes: ByteArray, identity: String, protected: Boolean): ByteArray
}

class KeystoreNotesCipher : NotesCipher {
    private val keys = mutableMapOf<String, SecretKey>()
    private var generation = ""
    override fun selectGeneration(id:String) {
        require(id.isEmpty() || id.matches(Regex("[a-f0-9-]{36}")))
        generation=id
    }
    @Synchronized private fun key(protected: Boolean, create: Boolean, generation:String): SecretKey {
        if (protected) NotesSession.requireUnlocked()
        require(generation.isEmpty() || generation.matches(Regex("[a-f0-9-]{36}")))
        val kind=if(protected) "private" else "local"
        val alias=if(generation.isEmpty()) "comfer.notes.$kind.v1" else "comfer.notes.$kind.v2.$generation"
        keys[alias]?.let { return it }
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { keys[alias]=it;return it }
        check(create) { "Notes key unavailable. Existing data has not been reset. Recover backed-up content by restoring a Comfer backup to a fresh installation." }
        val spec=KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
        if(protected){
            spec.setUserAuthenticationRequired(true)
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(300)
            spec.setInvalidatedByBiometricEnrollment(false)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(spec.build()) }.generateKey().also { keys[alias]=it }
    }
    override fun seal(bytes:ByteArray,identity:String,protected:Boolean):ByteArray {
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(protected,true,generation))
        c.updateAAD(identity.toByteArray(Charsets.UTF_8))
        val id=generation.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(2,id.size.toByte())+id+c.iv+c.doFinal(bytes)
    }
    override fun open(bytes:ByteArray,identity:String,protected:Boolean):ByteArray {
        require(bytes.size>=29){"Invalid encrypted Notes data"}
        val start:Int;val id:String
        if(bytes[0]==1.toByte()){start=1;id=""} else {
            require(bytes[0]==2.toByte());val length=bytes[1].toInt();require(length==0||length==36)
            require(bytes.size>=2+length+28);id=bytes.copyOfRange(2,2+length).toString(Charsets.US_ASCII);start=2+length
        }
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(protected,false,id),GCMParameterSpec(128,bytes.copyOfRange(start,start+12)))
        c.updateAAD(identity.toByteArray(Charsets.UTF_8));return c.doFinal(bytes.copyOfRange(start+12,bytes.size))
    }
}

/** In addition to Keystore authentication: immediate re-lock on background/device lock. */
object NotesSession {
    @Volatile private var expires=0L
    fun authorize(){expires=android.os.SystemClock.elapsedRealtime()+290_000}
    fun lock(){expires=0}
    fun unlocked()=expires>android.os.SystemClock.elapsedRealtime()
    fun requireUnlocked(){if(!unlocked())throw NotesLocked()}
}
