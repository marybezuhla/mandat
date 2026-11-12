package ua.mandat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.mandat.data.Person
import ua.mandat.data.TaskStatus
import ua.mandat.data.TeamRole
import ua.mandat.data.cleanPerson
import ua.mandat.data.isOverdue

class PeopleTest {
    private fun person(name: String) = Person(
        name = name, organization = " Вигадана установа ", jobTitle = "", contact = "", notes = "",
        teamRole = TeamRole.ASSISTANT.name, createdAt = 0, updatedAt = 0,
    )

    @Test fun nameIsRequiredAndFieldsAreTrimmed() {
        assertNull(cleanPerson(person("  ")))
        val p = cleanPerson(person(" Вигадана Особа "))!!
        assertEquals("Вигадана Особа", p.name)
        assertEquals("Вигадана установа", p.organization)
    }

    @Test fun overdueOnlyWhenOpenAndPastTheDay() {
        val today = 1_000_000L
        assertTrue(isOverdue(today - 1, TaskStatus.OPEN.name, today))
        assertFalse(isOverdue(today, TaskStatus.OPEN.name, today))
        assertFalse(isOverdue(today - 1, TaskStatus.DONE.name, today))
        assertFalse(isOverdue(null, TaskStatus.OPEN.name, today))
    }

    @Test fun unknownKeysAreSafe() {
        assertNull(TeamRole.of("SOMETHING"))
        assertNull(TeamRole.of(null))
        assertEquals(TaskStatus.OPEN, TaskStatus.of("?"))
    }
}

class SuggestCategoryTest {
    @org.junit.Test fun rules() {
        org.junit.Assert.assertEquals(ua.mandat.data.PersonCategory.OFFICIAL, ua.mandat.data.suggestCategory("Міністерство фінансів", "Директор департаменту"))
        org.junit.Assert.assertEquals(ua.mandat.data.PersonCategory.NGO, ua.mandat.data.suggestCategory("ГО «Тестова»", ""))
        org.junit.Assert.assertEquals(ua.mandat.data.PersonCategory.JOURNALIST, ua.mandat.data.suggestCategory("Телеканал «Тест»", "Ведучий"))
        org.junit.Assert.assertEquals(ua.mandat.data.PersonCategory.EXPERT, ua.mandat.data.suggestCategory("Університет", "Доцент"))
        org.junit.Assert.assertEquals(ua.mandat.data.PersonCategory.COLLEAGUE_MP, ua.mandat.data.suggestCategory("Фракція «Тест»", "Народний депутат"))
        org.junit.Assert.assertEquals(null, ua.mandat.data.suggestCategory("", ""))
    }

    @org.junit.Test fun aDeputyAndAPlainColleagueAreTwoCategories() {
        val c = ua.mandat.data.PersonCategory.COLLEAGUE
        fun someone(category: String?, more: String = "") = ua.mandat.data.Person(
            name = "Вигадана Особа", organization = "", jobTitle = "", contact = "", notes = "", teamRole = null,
            createdAt = 0, updatedAt = 0, category = category, moreCategories = more,
        )
        // For elected work colleagues are deputies: the plain "colleague" is not offered...
        val elected = ua.mandat.data.standardCategories(elected = true, people = listOf(someone("COLLEAGUE_MP")))
        org.junit.Assert.assertTrue(ua.mandat.data.PersonCategory.COLLEAGUE_MP in elected && c !in elected)
        // ...unless someone already has it (given in another mode), also as a second category
        org.junit.Assert.assertTrue(c in ua.mandat.data.standardCategories(true, listOf(someone("COLLEAGUE"))))
        org.junit.Assert.assertTrue(c in ua.mandat.data.standardCategories(true, listOf(someone("EXPERT", more = "COLLEAGUE"))))
        // An official or a citizen has both: colleagues at work, and deputies they deal with
        val plain = ua.mandat.data.standardCategories(elected = false, people = emptyList())
        org.junit.Assert.assertTrue(c in plain && ua.mandat.data.PersonCategory.COLLEAGUE_MP in plain)
        org.junit.Assert.assertTrue(ua.mandat.data.PersonCategory.OTHER !in plain && ua.mandat.data.PersonCategory.OTHER !in elected)
    }
}
