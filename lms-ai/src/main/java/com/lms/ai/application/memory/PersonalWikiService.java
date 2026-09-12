package com.lms.ai.application.memory;

import com.lms.ai.application.port.out.PersonalWikiPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Application use cases for explicitly managed personal Wiki memory. */
@Service
@RequiredArgsConstructor
public class PersonalWikiService {

    private final PersonalWikiPort wikiPort;

    public PersonalWikiPort.MemoryRef remember(Long userId, String title, String content, String description) {
        return wikiPort.remember(new PersonalWikiPort.RememberCommand(userId, title, content, description));
    }

    public void correct(Long userId, String memoryId, String oldText, String newText) {
        wikiPort.correct(new PersonalWikiPort.CorrectCommand(userId, memoryId, oldText, newText));
    }

    public void forget(Long userId, String memoryId) {
        wikiPort.forget(new PersonalWikiPort.ForgetCommand(userId, memoryId));
    }
}
