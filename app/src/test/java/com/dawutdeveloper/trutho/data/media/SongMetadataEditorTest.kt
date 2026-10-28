package com.dawutdeveloper.trutho.data.media

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.*
import org.junit.Test

class SongMetadataEditorTest {
    @Test fun writesUnicodeTagsToRealWavWithoutChangingAudio() {
        val file = File.createTempFile("metadata-test", ".wav")
        try {
            val samples = ByteArray(16000) { (it % 127).toByte() }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVE".toByteArray())
                .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(8000).putInt(16000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(samples.size).array()
            file.writeBytes(header + samples)
            SongMetadataEditor.writeTags(file, "????? ????? ? O?zbek", "???????????")
            val read = AudioFileIO.read(file)
            assertEquals("????? ????? ? O?zbek", read.tag.getFirst(FieldKey.TITLE))
            assertEquals("???????????", read.tag.getFirst(FieldKey.ARTIST))
            val bytes = file.readBytes()
            val dataOffset = bytes.indices.first { index -> index + 8 + samples.size <= bytes.size &&
                bytes.copyOfRange(index, index + 4).contentEquals("data".toByteArray()) } + 8
            assertArrayEquals(samples, bytes.copyOfRange(dataOffset, dataOffset + samples.size))
            SongMetadataEditor.writeTags(file, "Renamed again", "")
            assertEquals("Renamed again", AudioFileIO.read(file).tag.getFirst(FieldKey.TITLE))
            assertEquals("", AudioFileIO.read(file).tag.getFirst(FieldKey.ARTIST))
        } finally { file.delete() }
    }
}
