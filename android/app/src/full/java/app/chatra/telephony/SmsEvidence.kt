package app.chatra.telephony

import android.app.Activity
import android.telephony.SmsManager
import app.chatra.core.model.PartStatus

/** These results reject submission locally. Generic/network/modem failures remain uncertain. */
internal fun submissionResult(code: Int): PartStatus = when (code) {
    Activity.RESULT_OK -> PartStatus.Sent
    SmsManager.RESULT_ERROR_RADIO_OFF, SmsManager.RESULT_ERROR_NO_SERVICE,
    SmsManager.RESULT_ERROR_NULL_PDU, SmsManager.RESULT_ERROR_LIMIT_EXCEEDED,
    SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE, SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
    SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED, SmsManager.RESULT_INVALID_ARGUMENTS,
    SmsManager.RESULT_INVALID_SMS_FORMAT, SmsManager.RESULT_ENCODING_ERROR,
    SmsManager.RESULT_INVALID_SMSC_ADDRESS, SmsManager.RESULT_NO_DEFAULT_SMS_APP -> PartStatus.Failed
    else -> PartStatus.Unknown
}
