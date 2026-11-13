package ua.mandat

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.mandat.data.Mode
import ua.mandat.data.RolePost

class RolePostTest {
    @Test fun guessesThePostFromTheName() {
        assertEquals(RolePost.LEAD, RolePost.guess("Голова — Комісія А"))
        assertEquals(RolePost.LEAD, RolePost.guess("Група Б — керівниця групи"))
        assertEquals(RolePost.DEPUTY, RolePost.guess("Заступниця голови — Фракція «Приклад»"))
        assertEquals(RolePost.MEMBER, RolePost.guess("Член — Комісія В"))
        assertEquals(RolePost.SECRETARY, RolePost.guess("Secretary — Committee"))
        assertEquals(null, RolePost.guess("Секретаріат ради"))
        assertEquals(null, RolePost.guess("Headquarters"))
        assertEquals(null, RolePost.guess("Фракція «Приклад»"))
    }

    @Test fun postsOfOfficialsAndCitizensAreGuessedToo() {
        assertEquals(RolePost.LEAD, RolePost.guess("Керівник підрозділу — Міністерство чи відомство"))
        assertEquals(RolePost.DEPUTY, RolePost.guess("Заступник керівника — Установа чи комунальне підприємство"))
        assertEquals(RolePost.MEMBER, RolePost.guess("Член правління — Громадська організація чи фонд «Приклад»"))
        assertEquals(RolePost.LEAD, RolePost.guess("Head of unit — Ministry or agency"))
        assertEquals(RolePost.MEMBER, RolePost.guess("Member of the board — NGO or charitable foundation"))
        // No leading word in the name: left for the owner to say
        assertEquals(null, RolePost.guess("Фахівець — Обласна чи районна адміністрація"))
        assertEquals(null, RolePost.guess("Координатор — Ініціативна група"))
    }

    @Test fun modeIsReadByNameAndFallsBackToTheParliament() {
        assertEquals(Mode.COUNCIL, Mode.of("COUNCIL"))
        assertEquals(Mode.ASSISTANT, Mode.of("ASSISTANT"))
        assertEquals(Mode.OFFICIAL, Mode.of("OFFICIAL"))
        assertEquals(Mode.CITIZEN, Mode.of("CITIZEN"))
        // Nothing chosen yet, or a value from a newer Mandat
        assertEquals(Mode.RADA, Mode.of(null))
        assertEquals(Mode.RADA, Mode.of("SOMETHING_NEWER"))
    }

    @Test fun anAssistantWorksOnADeputysMattersButIsNotOne() {
        // The wording of elected work applies (district, sessions, appeals)...
        assertEquals(listOf(Mode.RADA, Mode.COUNCIL, Mode.ASSISTANT), Mode.entries.filter { it.elected })
        // ...but a deputy is not a colleague, and the assistant has no post in the deputy's bodies
        assertEquals(listOf(Mode.RADA, Mode.COUNCIL), Mode.entries.filter { it.deputy })
        assertEquals(null, RolePost.guess("Помічник-консультант — Народний депутат «Вигадане Прізвище»"))
        assertEquals(null, RolePost.guess("Assistant-consultant — People's Deputy of Ukraine"))
        assertEquals(null, RolePost.guess("Voluntary assistant-consultant — Local councillor"))
        assertEquals(RolePost.LEAD, RolePost.guess("Керівник — Громадська приймальня"))
    }

    @org.junit.Test fun roleChipsTellRolesApart() {
        // Different posts: the post alone is enough
        org.junit.Assert.assertEquals(listOf("Голова", "Член"), ua.mandat.ui.shortRoles(listOf("Голова — Комітет «з питань бюджету»", "Член — Фракція")))
        // The same post twice: each shows its own name, or else its body
        org.junit.Assert.assertEquals(
            listOf("з питань бюджету", "Тимчасова слідча комісія", "Голова"),
            ua.mandat.ui.shortRoles(listOf("Член — Комітет «з питань бюджету»", "Член — Тимчасова слідча комісія", "Голова — Підкомітет")),
        )
        org.junit.Assert.assertEquals(listOf("Громадянин"), ua.mandat.ui.shortRoles(listOf("Громадянин")))
        org.junit.Assert.assertEquals(mapOf(7L to "Перша", 9L to "Друга"), ua.mandat.ui.shortRoleNames(mapOf(7L to "Член — Рада «Перша»", 9L to "Член — Рада «Друга»")))
    }
}
