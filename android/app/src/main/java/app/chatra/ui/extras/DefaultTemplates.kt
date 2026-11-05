package app.chatra.ui.extras

import app.chatra.core.model.Template
import app.chatra.core.model.TemplateId

/** Szablony startowe (CH034 doda edycję i trwałe przechowywanie). */
val DefaultTemplates = listOf(
    Template(TemplateId("t-late"), "Spóźnię się", "Spóźnię się około 10 minut.", 0),
    Template(TemplateId("t-call"), "Oddzwonię", "Nie mogę teraz rozmawiać, oddzwonię później.", 1),
    Template(TemplateId("t-way"), "Jestem w drodze", "Jestem w drodze, będę niedługo.", 2),
    Template(TemplateId("t-thanks"), "Dziękuję", "Dziękuję, wszystko dotarło.", 3),
)
