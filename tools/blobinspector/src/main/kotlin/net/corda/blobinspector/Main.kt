package net.corda.blobinspector

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.jcabi.manifests.Manifests
import net.corda.client.jackson.JacksonSupport
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.ByteSequence
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.amqpMagic
import picocli.CommandLine
import picocli.CommandLine.*
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths

@Command(
        name = "Blob Inspector",
        versionProvider = VersionProvider::class,
        mixinStandardHelpOptions = true, // add --help and --version options,
        showDefaultValues = true,
        description = ["Inspect AMQP serialised binary blobs"]
)
class Main : Runnable {
    @Parameters(index = "0", paramLabel = "SOURCE", description = ["URL or file path to the blob"], converter = [SourceConverter::class])
    private var source: URL? = null

    @Option(names = ["--format"], paramLabel = "type", description = ["Output format. Possible values: [YAML, JSON]"])
    private var formatType: FormatType = FormatType.YAML

    @Option(names = ["--schema"], description = ["Print the blob's schema first"])
    private var schema: Boolean = false

    override fun run() {
        val bytes = source!!.readBytes()

        if (schema) {
            val envelope = DeserializationInput.getEnvelope(ByteSequence.of(bytes))
            println(envelope.schema)
            println()
        }

        initialiseSerialization()

        val factory = when (formatType) {
            FormatType.YAML -> YAMLFactory()
            FormatType.JSON -> JsonFactory()
        }
        val mapper = JacksonSupport.createNonRpcMapper(factory)

        val deserialized = bytes.deserialize<Any>()
        println(deserialized.javaClass.name)
        mapper.writeValue(System.out, deserialized)
    }

    private fun initialiseSerialization() {
        _contextSerializationEnv.set(SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPInspectorSerializationScheme)
                },
                AMQP_P2P_CONTEXT)
        )
    }
}

private object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == amqpMagic && target == SerializationContext.UseCase.P2P
    }
    override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
}

private class SourceConverter : ITypeConverter<URL> {
    override fun convert(value: String): URL {
        return try {
            URL(value)
        } catch (e: MalformedURLException) {
            Paths.get(value).toUri().toURL()
        }
    }
}

private class VersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> = arrayOf(Manifests.read("Corda-Release-Version"))
}

private enum class FormatType { YAML, JSON }

fun main(args: Array<String>) = CommandLine.run(Main(), *args)
