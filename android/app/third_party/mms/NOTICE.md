# MMS PDU codec

Source: https://github.com/klinker41/android-smsmms/tree/f6fa7d379444dc11d700fe6178b418bd76cfb88f/library/src/main/java/com/google/android/mms/pdu_alt

Upstream commit f6fa7d379444dc11d700fe6178b418bd76cfb88f (2020-09-11). Apache-2.0, Copyright Android Open Source Project and Jacob Klinker; per-file notices retained. Upstream has no recent maintenance. Evaluated against AOSP Messaging (also legacy) and GPL alternatives; only the bounded PDU codec is vendored. No upstream transport, APN editor, manifest, logger, or dependencies are included. Chatra owns the platform transport and validation.

Local changes: package relocation; Android logger; no subscriber-number lookup or external logger; bounds for multipart counts/lengths; nested multipart rejected. Decoder input is capped by Chatra at 1 MiB. `upstream-sha256.json` records original file hashes. Format and fuzz tests cover this integration; carrier acceptance still requires physical devices.
