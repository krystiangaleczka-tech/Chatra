package app.chatra.mms

import app.chatra.mms.pdu.GenericPdu
import app.chatra.mms.pdu.PduParser

/** A malformed network PDU is a decode failure even when a legacy codec assertion is enabled. */
object MmsCodec {
    fun parse(bytes: ByteArray): GenericPdu? {
        if (bytes.isEmpty() || bytes.size > MmsEngine.MAX_PDU) return null
        return try { PduParser(bytes, true).parse() }
        catch (_: RuntimeException) { null }
        catch (_: AssertionError) { null }
    }
}
