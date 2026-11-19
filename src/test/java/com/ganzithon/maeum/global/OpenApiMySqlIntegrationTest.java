package com.ganzithon.maeum.global;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ganzithon.maeum.activity.ActivityMySqlTestSupport;

import org.junit.jupiter.api.Test;

class OpenApiMySqlIntegrationTest extends ActivityMySqlTestSupport {
    @Test
    void apiDocsAndSwaggerUiAreOpenWithoutLogin() throws Exception {
        var docs = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        assertEquals(200, docs.getStatus());
        var paths = mapper.readTree(docs.getContentAsByteArray()).get("paths");
        assertTrue(paths.has("/api/v1/concerns"));
        assertTrue(paths.has("/api/v1/island/items"));
        assertTrue(
                mapper.readTree(docs.getContentAsByteArray())
                        .at("/components/securitySchemes/bearerAuth")
                        .has("scheme"));
        assertEquals(200, mvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus());
    }

    @Test
    void apisStillRequireLogin() throws Exception {
        assertEquals(401, mvc.perform(get("/api/v1/concerns")).andReturn().getResponse().getStatus());
    }
}
