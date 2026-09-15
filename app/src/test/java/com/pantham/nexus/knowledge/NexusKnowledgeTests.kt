package com.pantham.nexus.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusKnowledgeTests {

    @Test
    fun entityCanBeRemembered() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val person = controller.rememberPerson(
            name = "Rahul",
            aliases = setOf("Rahul bhai"),
            attributes = mapOf("role" to "friend")
        )

        val results = controller.personalEngine.search("Rahul")

        assertTrue(results.isNotEmpty())
        assertEquals(person.id, results.first().entity.id)
    }

    @Test
    fun aliasResolutionWorks() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val person = controller.rememberPerson(
            name = "Rahul Sharma",
            aliases = setOf("Rahul bhai", "Bunty")
        )

        val searchByAlias = controller.personalEngine.search("Rahul bhai")
        assertTrue(searchByAlias.isNotEmpty())
        assertEquals(person.id, searchByAlias.first().entity.id)
    }

    @Test
    fun relationshipPathWorks() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val rahul = controller.rememberPerson("Rahul")
        val amit = controller.rememberPerson("Amit")

        controller.connectPeople(
            rahul,
            RelationshipType.FRIEND_OF,
            amit
        )

        val path = controller.explainConnection("Rahul", "Amit")

        assertNotNull(path)
        assertEquals(2, path?.entities?.size)
    }

    @Test
    fun multiHopGraphTraversal() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val rahul = controller.rememberPerson("Rahul")
        val companyX = KnowledgeEntity(
            type = KnowledgeEntityType.ORGANIZATION,
            canonicalName = "Company X",
            source = KnowledgeSource.USER_EXPLICIT
        )
        val projectY = KnowledgeEntity(
            type = KnowledgeEntityType.PROJECT,
            canonicalName = "Project Y",
            source = KnowledgeSource.USER_EXPLICIT
        )

        controller.personalEngine.relate(rahul, RelationshipType.WORKS_AT, companyX)
        controller.personalEngine.relate(companyX, RelationshipType.OWNS, projectY)

        val path = controller.explainConnection("Rahul", "Project Y")
        assertNotNull(path)
        assertEquals(3, path?.entities?.size)
        assertEquals("Rahul", path?.entities?.get(0)?.canonicalName)
        assertEquals("Company X", path?.entities?.get(1)?.canonicalName)
        assertEquals("Project Y", path?.entities?.get(2)?.canonicalName)
    }

    @Test
    fun factsAreStored() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val person = controller.rememberPerson("Rahul")

        controller.personalEngine.rememberFact(
            entity = person,
            predicate = "favoriteColor",
            value = "blue"
        )

        val context = controller.personalEngine.getPersonContext("Rahul")

        assertTrue(
            context.relevantObservations.any {
                it.value == "blue"
            }
        )
    }

    @Test
    fun confidenceMergingWorks() {
        val engine = NexusKnowledgeConfidenceEngine()
        val merged = engine.merge(
            oldConfidence = KnowledgeConfidence.LOW,
            newConfidence = KnowledgeConfidence.HIGH,
            source = KnowledgeSource.USER_EXPLICIT
        )
        assertTrue(
            merged == KnowledgeConfidence.MEDIUM ||
            merged == KnowledgeConfidence.HIGH ||
            merged == KnowledgeConfidence.VERY_HIGH
        )
    }

    @Test
    fun conflictDetectionWorks() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val rahul = controller.rememberPerson("Rahul")
        controller.personalEngine.rememberFact(
            entity = rahul,
            predicate = "worksAt",
            value = "Company A",
            source = KnowledgeSource.USER_EXPLICIT
        )
        controller.personalEngine.rememberFact(
            entity = rahul,
            predicate = "worksAt",
            value = "Company B",
            source = KnowledgeSource.CONVERSATION
        )

        val conflicts = controller.personalEngine.detectConflicts(rahul.id)
        assertEquals(1, conflicts.size)
        assertEquals("worksAt", conflicts.first().key)
        assertEquals("Company A", conflicts.first().existingValue)
        assertEquals("Company B", conflicts.first().newValue)
    }

    @Test
    fun privacyGateRespectsInferenceAndSensitivity() {
        val gate = NexusKnowledgePrivacyGate()

        val allowed = gate.evaluate(
            source = KnowledgeSource.USER_EXPLICIT,
            sensitive = true,
            userExplicit = true
        )
        assertEquals(NexusKnowledgePrivacyGate.Decision.ALLOW, allowed)

        val needsConfirm = gate.evaluate(
            source = KnowledgeSource.OBSERVATION,
            sensitive = true,
            userExplicit = false
        )
        assertEquals(NexusKnowledgePrivacyGate.Decision.REQUIRE_CONFIRMATION, needsConfirm)

        val denied = gate.evaluate(
            source = KnowledgeSource.INFERRED,
            sensitive = true,
            userExplicit = false
        )
        assertEquals(NexusKnowledgePrivacyGate.Decision.DENY, denied)
    }

    @Test
    fun unknownPersonDoesNotCrash() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val context = controller.personalEngine.getPersonContext("Unknown Person")

        assertTrue(context.relevantEntities.isEmpty())
    }

    @Test
    fun emptyQueryReturnsSafeResults() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)

        val answer = controller.ask("")
        assertNotNull(answer)
        assertTrue(answer.entities.isEmpty())
    }

    @Test
    fun integrationBridgeAugmentsContext() = runBlocking {
        val repository = InMemoryKnowledgeRepository()
        val controller = NexusKnowledgeController(repository)
        val bridge = NexusKnowledgeIntegrationBridge(controller)

        val augmented = bridge.augment("my friend is Rahul")
        assertNotNull(augmented)
        assertEquals("my friend is Rahul", augmented.originalText)
        assertTrue(
            augmented.knowledgeContext.relevantEntities.any {
                it.canonicalName.contains("Rahul", ignoreCase = true)
            }
        )
    }
}
