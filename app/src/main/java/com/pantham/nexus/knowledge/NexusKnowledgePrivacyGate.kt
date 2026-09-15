package com.pantham.nexus.knowledge

class NexusKnowledgePrivacyGate {

    enum class Decision {
        ALLOW,
        REQUIRE_CONFIRMATION,
        DENY
    }

    fun evaluate(
        source: KnowledgeSource,
        sensitive: Boolean,
        userExplicit: Boolean
    ): Decision {

        if (
            source == KnowledgeSource.INFERRED &&
            sensitive
        ) {
            return Decision.DENY
        }

        if (sensitive && !userExplicit) {
            return Decision.REQUIRE_CONFIRMATION
        }

        return Decision.ALLOW
    }
}
