package io.a2a.server.apps.spring;

import io.a2a.spec.AgentCard;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @Description:
 * @Author: chenkangqiang
 * @Date: 2025/7/9
 */
public class AgentCardPathResolver {

    /**
     * 解析agent的basePath
     *
     * @param agentCard
     * @return
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
