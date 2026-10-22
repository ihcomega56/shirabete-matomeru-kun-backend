package jp.shirabete.backend.research.foundry;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public interface FoundryAgentSession extends AutoCloseable {
    Response respond(ResponseCreateParams params);

    @Override
    void close();
}
