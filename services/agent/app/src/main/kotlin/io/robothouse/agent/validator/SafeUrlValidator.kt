package io.robothouse.agent.validator

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import kotlin.reflect.KClass

/**
 * Bean Validation constraint that rejects URLs pointing to private networks,
 * loopback addresses, or cloud metadata endpoints to prevent SSRF attacks.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [SafeUrlValidator::class])
annotation class SafeUrl(
    val message: String = "URL is not allowed (private network or invalid scheme)",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/**
 * Validator for the [SafeUrl] constraint.
 *
 * Parses the URL, rejects non-HTTP schemes, resolves the hostname to IP
 * addresses, and blocks any that fall within private or reserved ranges.
 *
 * Covers all ranges that Java's [InetAddress] helpers miss: CGNAT
 * (`100.64.0.0/10`), benchmarking (`198.18.0.0/15`), IETF protocol
 * assignments (`192.0.0.0/24`), documentation/TEST-NET ranges, IPv6
 * ULA (`fc00::/7`), and IPv4-mapped IPv6 addresses.
 *
 * SSRF protection is applied at two points: at tool creation time via
 * this validator, and at runtime before each HTTP call in the executor.
 * The original URL is preserved for the actual request to ensure TLS/SNI
 * and virtual host routing work correctly.
 */
class SafeUrlValidator : ConstraintValidator<SafeUrl, String> {

    companion object {
        private val BLOCKED_HOSTNAMES = setOf(
            "localhost",
            "metadata.google.internal",
            "metadata.internal"
        )

        /**
         * Returns `true` if the given address falls within any private,
         * reserved, or non-routable range.
         *
         * Checks Java's built-in classifications (loopback, link-local,
         * site-local, any-local) plus additional ranges that Java does
         * not classify:
         * - `100.64.0.0/10` — CGNAT shared address space (RFC 6598)
         * - `198.18.0.0/15` — Benchmarking (RFC 2544)
         * - `192.0.0.0/24` — IETF protocol assignments (RFC 6890)
         * - `192.0.2.0/24` — TEST-NET-1 (RFC 5737)
         * - `198.51.100.0/24` — TEST-NET-2 (RFC 5737)
         * - `203.0.113.0/24` — TEST-NET-3 (RFC 5737)
         * - `fc00::/7` — IPv6 Unique Local Addresses (RFC 4193)
         * - IPv4-mapped IPv6 (`::ffff:x.x.x.x`) — checked by
         *   unwrapping to the embedded IPv4 address
         * - `169.254.169.254` — Cloud metadata endpoint
         */
        fun isPrivateOrReserved(address: InetAddress): Boolean {
            // Java built-in checks: loopback, link-local, site-local, any-local
            if (address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isAnyLocalAddress
            ) {
                return true
            }

            val raw = address.address

            when (address) {
                is Inet4Address -> {
                    val b0 = raw[0].toInt() and 0xFF
                    val b1 = raw[1].toInt() and 0xFF
                    val b2 = raw[2].toInt() and 0xFF

                    // CGNAT 100.64.0.0/10
                    if (b0 == 100 && (b1 and 0xC0) == 64) return true

                    // Benchmarking 198.18.0.0/15
                    if (b0 == 198 && (b1 and 0xFE) == 18) return true

                    // IETF protocol assignments 192.0.0.0/24
                    if (b0 == 192 && b1 == 0 && b2 == 0) return true

                    // TEST-NET-1 192.0.2.0/24
                    if (b0 == 192 && b1 == 0 && b2 == 2) return true

                    // TEST-NET-2 198.51.100.0/24
                    if (b0 == 198 && b1 == 51 && b2 == 100) return true

                    // TEST-NET-3 203.0.113.0/24
                    if (b0 == 203 && b1 == 0 && b2 == 113) return true

                    // Cloud metadata 169.254.169.254
                    if (b0 == 169 && b1 == 254 && b2 == 169 && (raw[3].toInt() and 0xFF) == 254) return true
                }

                is Inet6Address -> {
                    // IPv6 ULA fc00::/7
                    if ((raw[0].toInt() and 0xFE) == 0xFC) return true

                    // IPv4-mapped IPv6 ::ffff:x.x.x.x — unwrap and check the IPv4 part
                    if (raw.size == 16 &&
                        raw[10] == 0xFF.toByte() && raw[11] == 0xFF.toByte() &&
                        raw.sliceArray(0..9).all { it == 0.toByte() }
                    ) {
                        val ipv4Bytes = raw.sliceArray(12..15)
                        val ipv4 = Inet4Address.getByAddress(ipv4Bytes) as Inet4Address
                        return isPrivateOrReserved(ipv4)
                    }

                    // Cloud metadata fd00::ec2
                    if (address.hostAddress == "fd00::ec2") return true
                }
            }

            return false
        }

        /**
         * Returns `true` if the URL uses an HTTP(S) scheme and resolves to
         * a non-private, non-reserved IP address.
         */
        fun isSafeUrl(url: String): Boolean {
            val uri = try {
                URI(url)
            } catch (_: Exception) {
                return false
            }

            if (uri.scheme !in listOf("http", "https")) return false

            val host = uri.host ?: return false
            if (host.lowercase() in BLOCKED_HOSTNAMES) return false

            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (_: Exception) {
                return false
            }

            return addresses.none { isPrivateOrReserved(it) }
        }
    }

    /**
     * Validates the given URL string. Null and blank values are considered
     * valid (use `@NotBlank` to reject those separately).
     */
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value.isNullOrBlank()) return true
        return isSafeUrl(value)
    }
}
