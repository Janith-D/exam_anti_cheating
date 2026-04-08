package com.example.anti_cheating_backend.dto.identity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedScorePacketDTO {
    private String sessionId;
    private String studentId;

    private Double faceSimilarity;
    private Double faceQuality;
    private Double faceLiveness;
    private Double faceSpoofProbability;

    private Double voiceSimilarity;
    private Double voiceSpoofProbability;

    private Double challengeScore;
    private Double behaviorScore;

    private ModelVersionsDTO modelVersions;
    private Integer latencyMs;
}
