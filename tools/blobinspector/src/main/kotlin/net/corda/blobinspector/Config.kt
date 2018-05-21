package net.corda.blobinspector

/**
 * Configuration data class for  the Blob Inspector.
 *
 * @property mode
 */
class Config {
    var schema: Boolean = false
    var transforms: Boolean = false
    var data: Boolean = false
    var verbose: Boolean = false
}
