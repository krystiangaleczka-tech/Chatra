package app.chatra.core.testing

import app.chatra.core.model.Address
import app.chatra.core.model.AttachmentId
import app.chatra.core.model.AttachmentSummary
import app.chatra.core.model.ConversationId
import app.chatra.core.model.ConversationSummary
import app.chatra.core.model.ConversationSummary.Preview
import app.chatra.core.model.ConversationSummary.Tag
import app.chatra.core.model.DeliveryStatus
import app.chatra.core.model.Direction
import app.chatra.core.model.HdInfo
import app.chatra.core.model.LatenessPolicy
import app.chatra.core.model.Message
import app.chatra.core.model.MessageRef
import app.chatra.core.model.OperationId
import app.chatra.core.model.Participant
import app.chatra.core.model.ProviderRef
import app.chatra.core.model.ProviderTransport
import app.chatra.core.model.RecipientMode
import app.chatra.core.model.ScheduleId
import app.chatra.core.model.ScheduledMessage
import app.chatra.core.model.SendOperation
import app.chatra.core.model.SendState
import app.chatra.core.model.SimCard
import app.chatra.core.model.SubscriptionId
import app.chatra.core.model.Transport
import app.chatra.core.testing.FixedClock.Companion.WARSAW
import app.chatra.core.testing.FixedClock.Companion.at
import java.time.Instant
import java.time.LocalDateTime

/**
 * Dane dziesięciu plansz (CH006), stan 29.10.2026 14:40. Stabilne ID, bez sieci i kontaktów
 * urządzenia. Wyjątki względem logiki produkcyjnej są wypisane w [Exceptions].
 */
object Fixture {
    val SIM1 = SimCard(SubscriptionId(1), 0, "Prywatna")
    val SIM2 = SimCard(SubscriptionId(2), 1, "Praca")
    val sims = listOf(SIM1, SIM2)

    object Ids {
        val Mama = ConversationId("c-mama")
        val Ola = ConversationId("c-ola")
        val Paczka24 = ConversationId("c-paczka24")
        val Tomek = ConversationId("c-tomek")
        val Courier = ConversationId("c-kurier")
        val Kasia = ConversationId("c-kasia")
        val Brygada = ConversationId("c-brygada")
        val Marek = ConversationId("c-marek")
    }

    val mama = Participant(Address("+48 600 100 200"), "Mama")
    val ola = Participant(Address("+48 605 300 400"), "Ola Nowak")
    val paczka24 = Participant(Address("Paczka24"), "Paczka24")
    val tomek = Participant(Address("+48 607 500 600"), "Tomek Wiśniewski")
    val courier = Participant(Address("+48 601 234 567"))
    val kasia = Participant(Address("+48 512 408 771"), "Kasia Zielińska")
    val marek = Participant(Address("+48 698 700 800"), "Marek (praca)")
    val kuba = Participant(Address("+48 609 111 222"), "Kuba")
    val brygada = listOf(kuba, Participant(Address("+48 609 333 444"), "Ania"), Participant(Address("+48 609 555 666"), "Piotr"))

    /** Odcień awatara z plansz; w produkcji odcień wynika z hasha uczestników. */
    val avatarTones: Map<ConversationId, String> = mapOf(
        Ids.Mama to "slate", Ids.Ola to "rose", Ids.Paczka24 to "mint", Ids.Tomek to "slate",
        Ids.Courier to "neutral", Ids.Kasia to "lilac", Ids.Brygada to "sky", Ids.Marek to "slate",
    )

    /** Wiersze S02 w kolejności planszy; podglądy literalne (patrz [Exceptions.InboxPreviews]). */
    val inbox: List<ConversationSummary> = listOf(
        ConversationSummary(Ids.Mama, listOf(mama), "Mama", at(2026, 10, 29, 14, 32),
            Preview.Text("Kupiłam bilety na niedzielę. Odbierzesz mnie z dworca?"), unreadCount = 2, pinned = true),
        ConversationSummary(Ids.Ola, listOf(ola), "Ola Nowak", at(2026, 10, 29, 13, 5),
            Preview.Text("Dotarły, wyglądają super. Ostre jak z aparatu!"), unreadCount = 0, pinned = true, hdCapable = true),
        ConversationSummary(Ids.Paczka24, listOf(paczka24), "Paczka24", at(2026, 10, 29, 12, 47),
            Preview.Text("Paczka czeka w automacie KRA01M. Kod odbioru: 482 915"), unreadCount = 1, pinned = false, tags = setOf(Tag.Code)),
        ConversationSummary(Ids.Tomek, listOf(tomek), "Tomek Wiśniewski", at(2026, 10, 29, 11, 20),
            Preview.Draft("Jasne, będę koło 19, wezmę…"), unreadCount = 0, pinned = false),
        ConversationSummary(Ids.Courier, listOf(courier), "+48 601 234 567", at(2026, 10, 29, 10, 58),
            Preview.Text("Dzień dobry, tu kurier. Będę za 20 min."), unreadCount = 1, pinned = false, tags = setOf(Tag.UnknownNumber)),
        ConversationSummary(Ids.Kasia, listOf(kasia), "Kasia Zielińska", at(2026, 10, 27, 19, 10),
            Preview.SendFailed, unreadCount = 0, pinned = false),
        ConversationSummary(Ids.Brygada, brygada, "Brygada rowerowa", at(2026, 10, 27, 17, 45),
            Preview.Text("To w sobotę 9:00 pod mostem?", senderPrefix = "Grupowy MMS · Kuba"), unreadCount = 0, pinned = false, isGroupMms = true),
    )

    /** Marek występuje w S06 i S08, ale nie w skrzynce S02; rozmowa istnieje, starsza niż widoczne wiersze. */
    val hiddenConversations: List<ConversationSummary> = listOf(
        ConversationSummary(Ids.Marek, listOf(marek), "Marek (praca)", at(2026, 10, 20, 9, 0),
            Preview.Text("Dzięki, przejrzę po spotkaniu."), unreadCount = 0, pinned = false),
    )

    private var seq = 0L
    private fun sms(c: ConversationId, dir: Direction, body: String, t: Instant, sender: Participant?, read: Boolean = true, status: DeliveryStatus = DeliveryStatus.None) =
        Message(MessageRef.Provider(ProviderRef(ProviderTransport.SMS, ++seq)), c, dir, sender?.address, body, t, read, SIM1.id, status)

    val OlaFailed = OperationId("op-ola-wino")
    val OlaPending = OperationId("op-ola-pasuje")

    /** Historia S03 (Ola) i wiadomości potrzebne dla S02 i S07. */
    val messages: Map<ConversationId, List<Message>> = run {
        seq = 0
        val ola = listOf(
            sms(Ids.Ola, Direction.Incoming, "Masz zdjęcia z wesela Ani? Te z MMS-a są rozmazane.", at(2026, 10, 28, 18, 40), this.ola),
            sms(Ids.Ola, Direction.Outgoing, "Jasne, wyślę w oryginale.", at(2026, 10, 28, 18, 42), null, status = DeliveryStatus.Sent),
            Message(MessageRef.Hd("hd-ola-wesele"), Ids.Ola, Direction.Outgoing, null, "", at(2026, 10, 28, 18, 50), true, SIM1.id,
                DeliveryStatus.Sent,
                attachments = listOf(1, 2, 3, 4).map { AttachmentSummary(AttachmentId("a-wesele-$it"), "image/jpeg", "Zdjęcie $it") },
                hd = HdInfo(itemCount = 4, totalBytes = ExtrasTotalBytes, downloadedBy = "Olę")),
            sms(Ids.Ola, Direction.Incoming, "Dotarły, wyglądają super. Ostre jak z aparatu!", at(2026, 10, 29, 13, 5), this.ola),
            sms(Ids.Ola, Direction.Outgoing, "To w sobotę o 18:00 u was?", at(2026, 10, 29, 13, 11), null, status = DeliveryStatus.Delivered)
                .copy(statusAt = at(2026, 10, 29, 13, 12)),
            Message(MessageRef.Local(OlaFailed), Ids.Ola, Direction.Outgoing, null, "Wezmę wino i deser.", at(2026, 10, 29, 13, 20), true, null,
                DeliveryStatus.Failed, OlaFailed),
            Message(MessageRef.Local(OlaPending), Ids.Ola, Direction.Outgoing, null, "Pasuje, do zobaczenia!", at(2026, 10, 29, 14, 40), true, SIM1.id,
                DeliveryStatus.PendingUndo, OlaPending),
            sms(Ids.Ola, Direction.Incoming, "Odbierzesz paczkę ode mnie w piątek?", at(2026, 10, 22, 9, 15), this.ola),
        ).sortedBy { it.timestamp }
        mapOf(
            Ids.Mama to listOf(
                sms(Ids.Mama, Direction.Incoming, "Kupiłam bilety na sobotę.", at(2026, 10, 29, 14, 30), mama, read = false),
                sms(Ids.Mama, Direction.Incoming, "Kupiłam bilety na niedzielę. Odbierzesz mnie z dworca?", at(2026, 10, 29, 14, 32), mama, read = false),
            ),
            Ids.Ola to ola,
            Ids.Paczka24 to listOf(
                sms(Ids.Paczka24, Direction.Incoming, "PACZKA DORĘCZONA. Dziękujemy!", at(2026, 9, 14, 16, 2), paczka24),
                sms(Ids.Paczka24, Direction.Incoming, "Twoja paczka czeka w automacie KRA01M. Kod odbioru: 482 915", at(2026, 10, 29, 12, 47), paczka24, read = false),
            ),
            Ids.Tomek to listOf(
                sms(Ids.Tomek, Direction.Incoming, "Wpadniesz dziś wieczorem?", at(2026, 10, 29, 11, 20), tomek),
            ),
            Ids.Courier to listOf(
                sms(Ids.Courier, Direction.Incoming, "Tu kurier, mam dla Pana paczkę, będę za 20 min.", at(2026, 10, 29, 10, 41), courier),
                sms(Ids.Courier, Direction.Incoming, "Dzień dobry, tu kurier. Będę za 20 min.", at(2026, 10, 29, 10, 58), courier, read = false),
            ),
            Ids.Kasia to listOf(
                Message(MessageRef.Local(OperationId("op-kasia")), Ids.Kasia, Direction.Outgoing, null, "Oddam książkę w czwartek.", at(2026, 10, 27, 19, 10), true, SIM1.id,
                    DeliveryStatus.Failed, OperationId("op-kasia")),
            ),
            Ids.Brygada to listOf(
                Message(MessageRef.Provider(ProviderRef(ProviderTransport.MMS, 501)), Ids.Brygada, Direction.Incoming, kuba.address,
                    "To w sobotę 9:00 pod mostem?", at(2026, 10, 27, 17, 45), true, SIM1.id),
            ),
            Ids.Marek to listOf(
                sms(Ids.Marek, Direction.Incoming, "Dzięki, przejrzę po spotkaniu.", at(2026, 10, 20, 9, 0), marek),
            ),
        )
    }

    /** Szkic Tomka (S02) i bieżące pole S03. */
    const val TomekDraft = "Jasne, będę koło 19, wezmę…"
    const val OlaComposer = "Kupię kwiaty dla Ani"

    // --- Harmonogram (S05, S06) -------------------------------------------------------------

    private fun op(id: String, c: ConversationId, to: List<Participant>, body: String, sim: SimCard, state: SendState, mode: RecipientMode = RecipientMode.Single, transport: Transport = Transport.SMS) =
        SendOperation(OperationId(id), c, transport, mode, to.map { it.address }, body, emptyList(), sim.id, state, 1, at(2026, 10, 26, 12, 0))

    private fun schedule(id: String, opId: String, ldt: LocalDateTime, policy: LatenessPolicy = LatenessPolicy.Ask) =
        ScheduledMessage(ScheduleId(id), OperationId(opId), ldt.atZone(WARSAW).toInstant(), ldt, WARSAW, policy, 1)

    data class ScheduledFixture(val schedule: ScheduledMessage, val operation: SendOperation, val title: String)

    val scheduled: List<ScheduledFixture> = listOf(
        ScheduledFixture(schedule("s-tomek", "op-s-tomek", LocalDateTime.of(2026, 10, 28, 7, 30)),
            op("op-s-tomek", Ids.Tomek, listOf(tomek), "Pamiętaj o kluczach do piwnicy.", SIM1, SendState.MISSED_NEEDS_DECISION), "Tomek Wiśniewski"),
        ScheduledFixture(schedule("s-mama", "op-s-mama", LocalDateTime.of(2026, 10, 30, 8, 0)),
            op("op-s-mama", Ids.Mama, listOf(mama), "Wszystkiego najlepszego z okazji imienin!", SIM1, SendState.SCHEDULED), "Mama"),
        ScheduledFixture(schedule("s-brygada", "op-s-brygada", LocalDateTime.of(2026, 10, 30, 20, 0)),
            op("op-s-brygada", Ids.Brygada, brygada + listOf(Participant(Address("+48 609 777 888"), "Ewa")), "Jutro 9:00 pod mostem, nie spóźnijcie się!", SIM1,
                SendState.SCHEDULED, RecipientMode.GroupMms, Transport.MMS), "Brygada rowerowa"),
        ScheduledFixture(schedule("s-ola", "op-s-ola", LocalDateTime.of(2026, 10, 31, 10, 0)),
            op("op-s-ola", Ids.Ola, listOf(ola), "Przypomnienie: dziś o 18:00 u Ani. Wezmę wino!", SIM1, SendState.SCHEDULED), "Ola Nowak"),
        ScheduledFixture(schedule("s-marek", "op-s-marek", LocalDateTime.of(2026, 11, 2, 9, 0)),
            op("op-s-marek", Ids.Marek, listOf(marek), "Raport jest na dysku, omówimy po spotkaniu.", SIM2, SendState.SCHEDULED), "Marek (praca)"),
    )

    /** Formularz S05: odbiorca Ola, treść, sobota 31.10 10:00, SIM 1, polityka „pytaj”. */
    const val ScheduleFormBody = "Przypomnienie: dziś o 18:00 u Ani. Wezmę wino!"
    val ScheduleFormDate: LocalDateTime = LocalDateTime.of(2026, 10, 31, 10, 0)

    /** Stopka S06: ostatnio wysłana zaplanowana wiadomość. */
    data class LastSent(val title: String, val at: Instant, val delivered: Boolean)
    val lastScheduledSent = LastSent("Ola Nowak", at(2026, 10, 27, 12, 0), delivered = true)

    // --- Nowa wiadomość (S08) ----------------------------------------------------------------

    val newMessageRecipients = listOf(mama, ola, tomek.copy(displayName = "Tomek W."))
    data class Suggestion(val participant: Participant, val detail: String, val tone: String, val initials: String)
    val suggestions = listOf(
        Suggestion(kasia, "komórka · +48 512 408 771", "lilac", "KZ"),
        Suggestion(marek, "służbowy · zwykle z SIM 2", "slate", "M"),
    )

    // --- Ustawienia (S10) --------------------------------------------------------------------

    const val BlockedCount = 3
    const val TrashCount = 4
    const val TrashDays = 30

    /** S04: cztery wybrane zdjęcia, 38,2 MB, odbiorca ma Chatrę. */
    const val ExtrasSelectedCount = 4
    const val ExtrasTotalBytes = 38_200_000L

    /**
     * Świadome wyjątki fixture względem logiki produkcyjnej (zachowane, bo wzorzec je pokazuje).
     */
    object Exceptions {
        /** S03: etykieta „21/160 · 1 SMS” jest statyczna w HTML; poprawnie: 20 znaków, Unicode (ę). */
        const val ComposerCounterLiteral = "21/160 · 1 SMS"

        /**
         * S02 pokazuje podglądy niezgodne z historią z S03/S07: Paczka24 „Paczka czeka…” zamiast
         * treści „Twoja paczka czeka…”, a Ola — ostatnią odebraną zamiast ostatniej wysłanej.
         * Wiersze skrzynki fixture są literalne; po nowej wiadomości w demo podgląd liczy się już normalnie.
         */
        const val InboxPreviews = true

        /** S09: podgląd rozmowy w kafelku to makieta z planszy, nie historia Oli. */
        val AppearancePreview = listOf("Dotarły, wyglądają super!", "To w sobotę o 18:00?")
    }
}
