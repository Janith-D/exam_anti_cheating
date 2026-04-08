package com.example.anti_cheating_backend.dto.identity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelVersionsDTO {
    private String faceDetector;
    private String faceEmbedder;
    private String faceSpoof;
    private String speaker;
    private String voiceSpoof;
}
