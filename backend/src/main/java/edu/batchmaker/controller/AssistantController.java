package edu.batchmaker.controller;

import edu.batchmaker.dto.assistant.AssistantAnswer;
import edu.batchmaker.dto.assistant.AssistantQuery;
import edu.batchmaker.service.AssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Natural-language questions about the published timetable. */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/query")
    public AssistantAnswer query(@Valid @RequestBody AssistantQuery request) {
        return assistantService.answer(request.question());
    }
}
