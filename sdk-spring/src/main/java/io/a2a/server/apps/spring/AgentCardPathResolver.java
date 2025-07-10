package io.a2a.server.apps.spring;

import io.a2a.spec.AgentCard;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Agent card path resolver utility
 */
public class AgentCardPathResolver {

    /**
     * Resolve the base path of the agent
     *
     * @param agentCard：agentCard
     * @return the resolved base path
     */
    public static String resolveBasePath(AgentCard agentCard) {
        String path = UriComponentsBuilder.fromUriString(agentCard.url())
                .build()
                .getPath();
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
