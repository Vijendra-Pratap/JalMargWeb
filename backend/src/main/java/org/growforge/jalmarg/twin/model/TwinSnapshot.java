package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.model.Enums.RiskLevel;
import org.growforge.jalmarg.model.Enums.RiskStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.TwinSnapshotType;

import java.time.Instant;

/**
 * Immutable digital twin state snapshot representing municipal risk, active hazards, and telemetry.
 */
@Entity
@Table(name = "twin_snapshots", indexes = {
        @Index(name = "idx_snapshot_type", columnList = "snapshotType"),
        @Index(name = "idx_snapshot_timestamp", columnList = "snapshotTimestamp"),
        @Index(name = "idx_snapshot_zone", columnList = "zone_id")
})
public class TwinSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TwinSnapshotType snapshotType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private MunicipalZone zone;

    @Column(length = 128)
    private String zoneName;

    @Column(nullable = false)
    private Instant snapshotTimestamp;

    @Column(nullable = false)
    private Instant publishedAt;

    private int overallRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskStatus riskStatus;

    private int activeReportCount;
    private int activeIncidentCount;

    @Column(name = "rainfall_6h_mm")
    private double rainfall6hMm;

    @Column(nullable = false, length = 64)
    private String modelVersion;

    @Column(columnDefinition = "TEXT")
    private String contributingEvidenceJson;

    @Column(columnDefinition = "TEXT")
    private String missingDataIndicatorsJson;

    @Column(length = 500)
    private String provenanceSummary;

    @Column(length = 500)
    private String uncertaintyDisclaimer;

    @Column(length = 64)
    private String immutableHash;

    public TwinSnapshot() {}

    public TwinSnapshot(TwinSnapshotType snapshotType, MunicipalZone zone, String zoneName,
                        Instant snapshotTimestamp, int overallRiskScore, RiskLevel riskLevel,
                        RiskStatus riskStatus, int activeReportCount, int activeIncidentCount,
                        double rainfall6hMm, String modelVersion, String contributingEvidenceJson,
                        String missingDataIndicatorsJson, String provenanceSummary,
                        String uncertaintyDisclaimer, String immutableHash) {
        this.snapshotType = snapshotType;
        this.zone = zone;
        this.zoneName = zoneName;
        this.snapshotTimestamp = snapshotTimestamp;
        this.publishedAt = Instant.now();
        this.overallRiskScore = overallRiskScore;
        this.riskLevel = riskLevel;
        this.riskStatus = riskStatus;
        this.activeReportCount = activeReportCount;
        this.activeIncidentCount = activeIncidentCount;
        this.rainfall6hMm = rainfall6hMm;
        this.modelVersion = modelVersion;
        this.contributingEvidenceJson = contributingEvidenceJson;
        this.missingDataIndicatorsJson = missingDataIndicatorsJson;
        this.provenanceSummary = provenanceSummary;
        this.uncertaintyDisclaimer = uncertaintyDisclaimer;
        this.immutableHash = immutableHash;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TwinSnapshotType getSnapshotType() { return snapshotType; }
    public void setSnapshotType(TwinSnapshotType snapshotType) { this.snapshotType = snapshotType; }

    public MunicipalZone getZone() { return zone; }
    public void setZone(MunicipalZone zone) { this.zone = zone; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public Instant getSnapshotTimestamp() { return snapshotTimestamp; }
    public void setSnapshotTimestamp(Instant snapshotTimestamp) { this.snapshotTimestamp = snapshotTimestamp; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public int getOverallRiskScore() { return overallRiskScore; }
    public void setOverallRiskScore(int overallRiskScore) { this.overallRiskScore = overallRiskScore; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public RiskStatus getRiskStatus() { return riskStatus; }
    public void setRiskStatus(RiskStatus riskStatus) { this.riskStatus = riskStatus; }

    public int getActiveReportCount() { return activeReportCount; }
    public void setActiveReportCount(int activeReportCount) { this.activeReportCount = activeReportCount; }

    public int getActiveIncidentCount() { return activeIncidentCount; }
    public void setActiveIncidentCount(int activeIncidentCount) { this.activeIncidentCount = activeIncidentCount; }

    public double getRainfall6hMm() { return rainfall6hMm; }
    public void setRainfall6hMm(double rainfall6hMm) { this.rainfall6hMm = rainfall6hMm; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getContributingEvidenceJson() { return contributingEvidenceJson; }
    public void setContributingEvidenceJson(String contributingEvidenceJson) { this.contributingEvidenceJson = contributingEvidenceJson; }

    public String getMissingDataIndicatorsJson() { return missingDataIndicatorsJson; }
    public void setMissingDataIndicatorsJson(String missingDataIndicatorsJson) { this.missingDataIndicatorsJson = missingDataIndicatorsJson; }

    public String getProvenanceSummary() { return provenanceSummary; }
    public void setProvenanceSummary(String provenanceSummary) { this.provenanceSummary = provenanceSummary; }

    public String getUncertaintyDisclaimer() { return uncertaintyDisclaimer; }
    public void setUncertaintyDisclaimer(String uncertaintyDisclaimer) { this.uncertaintyDisclaimer = uncertaintyDisclaimer; }

    public String getImmutableHash() { return immutableHash; }
    public void setImmutableHash(String immutableHash) { this.immutableHash = immutableHash; }
}
