package com.rodrigomachado.riff.desktop

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.rodrigomachado.riff.core.audio.FfmpegPcmSource
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.separation.DesktopSeparation
import org.lwjgl.glfw.GLFW
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.io.path.createTempDirectory
import kotlin.system.exitProcess

/**
 * Confere as peças nativas do app do PC: `./scripts/gw :desktop:runInstallCheck` (no Java do build) ou o executável
 * de conferência dentro do app empacotado (no Java que vai junto). Imprime `ok <peça>` ou `FALHOU <peça>: <erro>`
 * por linha e sai com 1 se alguma falhou.
 */
fun main() {
    val failures = InstallCheck.run(createTempDirectory("riff-check").toFile())
    val failed = failures.associateBy { it.part }
    InstallCheck.PARTS.forEach { part ->
        println(failed[part]?.let { "FALHOU $part: ${it.error}" } ?: "ok $part")
    }
    if (failures.isNotEmpty()) exitProcess(1)
}

/**
 * Cada peça com o mínimo que obriga a biblioteca nativa (ou o módulo do Java) a carregar, cada uma isolada: uma
 * quebrada não impede as outras. Os arquivos de teste vão para a pasta `work`.
 */
object InstallCheck {
    /** Uma peça que não carregou: o nome (um de [PARTS]) e o erro. */
    data class Failure(val part: String, val error: String)

    /** As peças, na ordem em que são conferidas e impressas. */
    val PARTS = listOf("onnxruntime", "modelo", "ffmpeg", "sqlite", "glfw", "tls")

    /** As peças que não carregaram; vazia quando tudo carregou. */
    fun run(work: File): List<Failure> = listOf<Pair<String, () -> Unit>>(
        "onnxruntime" to ::onnxRuntime,
        "modelo" to ::model,
        "ffmpeg" to { ffmpeg(work) },
        "sqlite" to { sqlite(work) },
        "glfw" to ::glfw,
        "tls" to ::tls,
    ).mapNotNull { (part, check) -> attempt(part, check) }

    // Uma biblioteca nativa ausente chega como UnsatisfiedLinkError (um Error): tudo vira falha da peça.
    @Suppress("TooGenericExceptionCaught")
    private fun attempt(part: String, check: () -> Unit): Failure? = try {
        check()
        null
    } catch (e: Throwable) {
        Failure(part, e.toString())
    }

    /** Abre e roda uma rede de identidade (um nó `Identity`, gerada aqui), sem o modelo de 130 MB. */
    private fun onnxRuntime() {
        val env = OrtEnvironment.getEnvironment()
        env.createSession(IdentityModel.bytes()).use { session ->
            OnnxTensor.createTensor(env, floatArrayOf(CHECK_VALUE)).use { input ->
                session.run(mapOf(IdentityModel.INPUT to input)).use { result ->
                    val out = (result[0] as OnnxTensor).floatBuffer[0]
                    check(out == CHECK_VALUE) { "a identidade devolveu $out" }
                }
            }
        }
    }

    /** O modelo da IA está nos recursos e é o arquivo inteiro (não o ponteiro do Git LFS); não é carregado. */
    private fun model() {
        val resource = javaClass.getResource(DesktopSeparation.MODEL_RESOURCE)
            ?: error("${DesktopSeparation.MODEL_RESOURCE} fora dos recursos")
        val bytes = resource.openConnection().contentLengthLong
        check(bytes > MIN_MODEL_BYTES) { "o modelo tem só $bytes bytes" }
    }

    /** Grava um `.wav` de 100 ms e abre com o FFmpeg (o decodificador do jogo). */
    private fun ffmpeg(work: File) {
        val wav = File(work, "check.wav")
        wav.writeBytes(silentWav())
        FfmpegPcmSource.open(wav.path, FileRandomReadOpener()).close()
    }

    /** Cria um banco com o SQLite embutido do Room, grava e lê uma linha. */
    private fun sqlite(work: File) {
        BundledSQLiteDriver().open(File(work, "check.db").path).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS conferencia (valor INTEGER)")
            db.execSQL("INSERT INTO conferencia (valor) VALUES ($SQL_VALUE)")
            db.prepare("SELECT valor FROM conferencia").use { row ->
                check(row.step()) { "a tabela voltou vazia" }
                check(row.getLong(0) == SQL_VALUE) { "a tabela devolveu ${row.getLong(0)}" }
            }
        }
    }

    /** Inicia e encerra o GLFW (o controle), sem janela. */
    private fun glfw() {
        check(GLFW.glfwInit()) { "glfwInit devolveu falso" }
        GLFW.glfwTerminate()
    }

    /** Os certificados do Windows, o ECDH (`jdk.crypto.ec`) e o TLS 1.3 confiando neles. */
    private fun tls() {
        // O Windows-ROOT vem do módulo jdk.crypto.mscapi; sem ele, o erro do KeyStore só diria "not found".
        check(ModuleLayer.boot().findModule(MSCAPI_MODULE).isPresent) { "$MSCAPI_MODULE fora do Java" }
        val roots = KeyStore.getInstance("Windows-ROOT").apply { load(null) }
        check(roots.size() > 0) { "o Windows-ROOT veio sem certificados" }
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val mine = keys.generateKeyPair()
        val theirs = keys.generateKeyPair()
        KeyAgreement.getInstance("ECDH").apply {
            init(mine.private)
            doPhase(theirs.public, true)
            generateSecret()
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(roots) }
        SSLContext.getInstance("TLSv1.3").init(null, trust.trustManagers, null)
    }

    /** 100 ms de silêncio, PCM de 16 bits, mono, 48 kHz. */
    private fun silentWav(): ByteArray {
        val dataBytes = WAV_RATE / WAV_MS_DIVISOR * 2
        return ByteBuffer.allocate(WAV_HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(WAV_HEADER_BYTES - 8 + dataBytes).put("WAVE".toByteArray())
            put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            putInt(WAV_RATE).putInt(WAV_RATE * 2).putShort(2).putShort(16)
            put("data".toByteArray()).putInt(dataBytes)
        }.array()
    }

    /** O módulo dos certificados do Windows (o `KeyStore` `Windows-ROOT`). */
    const val MSCAPI_MODULE = "jdk.crypto.mscapi"

    private const val CHECK_VALUE = 0.5f
    private const val SQL_VALUE = 42L
    private const val MIN_MODEL_BYTES = 100L shl 20
    private const val WAV_RATE = 48_000
    private const val WAV_MS_DIVISOR = 10
    private const val WAV_HEADER_BYTES = 44
}

/**
 * Um ONNX mínimo escrito à mão em protobuf: `y = Identity(x)`, `x` e `y` float de forma `[1]`, IR 7, opset 13.
 * Os números são os campos do `onnx.proto` (ModelProto, GraphProto, NodeProto, ValueInfoProto, TypeProto).
 */
private object IdentityModel {
    const val INPUT = "x"
    private const val OUTPUT = "y"

    fun bytes(): ByteArray = message(
        varint(field = 1, value = 7), // ModelProto.ir_version
        bytes(field = 7, message(*graph())), // ModelProto.graph
        bytes(field = 8, message(varint(field = 2, value = 13))), // ModelProto.opset_import (version)
    )

    private fun graph() = arrayOf(
        // GraphProto.node: input, output, op_type
        bytes(field = 1, message(text(1, INPUT), text(2, OUTPUT), text(4, "Identity"))),
        text(2, "conferencia"), // GraphProto.name
        bytes(field = 11, message(*floatOfOne(INPUT))), // GraphProto.input
        bytes(field = 12, message(*floatOfOne(OUTPUT))), // GraphProto.output
    )

    /** ValueInfoProto: o nome e o tipo tensor float (elem_type 1) de forma `[1]`. */
    private fun floatOfOne(name: String): Array<ByteArray> {
        val shape = message(bytes(field = 1, message(varint(field = 1, value = 1)))) // TensorShapeProto.dim
        val tensor = message(varint(field = 1, value = 1), bytes(field = 2, shape)) // TypeProto.Tensor
        return arrayOf(text(1, name), bytes(field = 2, message(bytes(field = 1, tensor))))
    }

    private fun message(vararg fields: ByteArray): ByteArray =
        ByteArrayOutputStream().apply { fields.forEach { write(it) } }.toByteArray()

    private fun text(field: Int, value: String) = bytes(field, value.toByteArray())

    /** Campo de tamanho prefixado (tipo 2). */
    private fun bytes(field: Int, value: ByteArray) = tag(field, 2) + rawVarint(value.size.toLong()) + value

    /** Campo inteiro (tipo 0). */
    private fun varint(field: Int, value: Long) = tag(field, 0) + rawVarint(value)

    private fun tag(field: Int, wireType: Int) = rawVarint((field shl 3 or wireType).toLong())

    private fun rawVarint(value: Long): ByteArray = ByteArrayOutputStream().apply {
        var rest = value
        while (rest >= 0x80) {
            write((rest and 0x7F or 0x80).toInt())
            rest = rest ushr 7
        }
        write(rest.toInt())
    }.toByteArray()
}
