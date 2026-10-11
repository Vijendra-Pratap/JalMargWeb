package org.growforge.jalmarg.twin.service;

import jakarta.annotation.PostConstruct;
import org.growforge.jalmarg.twin.model.DataSourceRegistry;
import org.growforge.jalmarg.twin.model.TwinEnums.SourceClassification;
import org.growforge.jalmarg.twin.model.TwinEnums.VerificationStatus;
import org.growforge.jalmarg.twin.repo.DataSourceRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TwinSourceRegistryService {

    private static final Logger log = LoggerFactory.getLogger(TwinSourceRegistryService.class);

    private final DataSourceRegistryRepository repo;

    public TwinSourceRegistryService(DataSourceRegistryRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultSources() {
        repo.findBySourceCode("SRC_DELHI_DRAINAGE_STATUS").ifPresent(src -> {
            if (src.getSourceClassification() != SourceClassification.UNAVAILABLE) {
                src.setSourceClassification(SourceClassification.UNAVAILABLE);
                repo.save(src);
            }
        });

        if (repo.count() > 0) return;

        List<DataSourceRegistry> defaults = List.of(
                new DataSourceRegistry(
                        "SRC_OPEN_METEO",
                        "Open-Meteo Weather Forecast (ECMWF IFS)",
                        "Open-Meteo / ECMWF",
                        "https://open-meteo.com/en/docs",
                        "Global / Delhi NCR (0.1 deg)",
                        "EPSG:4326",
                        "CC-BY-4.0",
                        "Research, Non-Commercial & Operational Forecasting",
                        SourceClassification.PROVIDER_FORECAST,
                        VerificationStatus.NUMERICAL_MODEL,
                        "Hourly",
                        "Forecast model output (precipitation sum and peak intensity). Never classified as ground-truth measured rainfall."
                ),
                new DataSourceRegistry(
                        "SRC_COPERNICUS_DEM",
                        "Copernicus DEM GLO-30 Topography",
                        "European Space Agency (ESA) / Airbus",
                        "https://spacedata.copernicus.eu",
                        "Global / Delhi NCR (30m DSM)",
                        "EPSG:4326 / EGM2008",
                        "Copernicus Open Access Policy",
                        "Terrain elevation, catchment slopes and depression profiling",
                        SourceClassification.DERIVED_ESTIMATE,
                        VerificationStatus.OFFICIAL_PRIMARY,
                        "Static (2021 release)",
                        "Digital Surface Model representing canopy/building tops. Does not measure sub-meter roadway underpass gutter elevations."
                ),
                new DataSourceRegistry(
                        "SRC_CITIZEN_CROWD",
                        "JalMarg Citizen Waterlogging Reports",
                        "JalMarg Citizen Network",
                        "https://jalmarg.org/api/reports",
                        "Delhi NCR (Latitude 28.2 to 28.9, Longitude 76.8 to 77.6)",
                        "EPSG:4326",
                        "Open Database License (ODbL)",
                        "Real-time hazard alerting and route detour advisories",
                        SourceClassification.USER_REPORTED,
                        VerificationStatus.COMMUNITY_CROWDSOURCED,
                        "Real-time / On-demand",
                        "Citizen crowdsourced depth picks and evidence photos. Filtered by anti-abuse rate limits and AI depth verification."
                ),
                new DataSourceRegistry(
                        "SRC_NASA_IMERG",
                        "NASA GPM IMERG Early Precipitation Run (V07B)",
                        "NASA Earth Science Data and Information System (ESDIS)",
                        "https://gpm.nasa.gov/data/imerg",
                        "Global (0.1 deg grid, ~4h latency)",
                        "EPSG:4326",
                        "NASA Open Data Policy",
                        "Satellite precipitation cross-validation",
                        SourceClassification.PROVIDER_FORECAST,
                        VerificationStatus.NUMERICAL_MODEL,
                        "Half-hourly (4-hour product latency)",
                        "Remote-sensing satellite microwave/infrared precipitation estimates. Subject to 4h latency."
                ),
                new DataSourceRegistry(
                        "SRC_HISTORICAL_HOTSPOTS",
                        "Delhi NCR Chronic Waterlogging Hotspots Registry",
                        "Delhi Traffic Police / Municipal Corporation of Delhi (MCD)",
                        "https://delhitrafficpolice.nic.in",
                        "Delhi NCR (66 Monitored Vulnerability Points)",
                        "EPSG:4326",
                        "Public Municipal Advisory",
                        "Prior historical vulnerability weighting",
                        SourceClassification.DERIVED_ESTIMATE,
                        VerificationStatus.OFFICIAL_PRIMARY,
                        "Monsoon Pre-season Update",
                        "Historical chronic waterlogging spots (e.g. Minto Bridge, Pul Prahladpur, Zakhira). Acts as historical susceptibility prior."
                ),
                new DataSourceRegistry(
                        "SRC_DELHI_DRAINAGE_STATUS",
                        "Delhi Municipal Drainage Telemetry Status",
                        "Delhi Jal Board (DJB) / PWD Drainage Division",
                        "https://delhijalboard.delhi.gov.in",
                        "Delhi NCT Primary Drainage Outfalls",
                        "EPSG:4326",
                        "Government of NCT Delhi",
                        "Infrastructure Planning",
                        SourceClassification.UNAVAILABLE,
                        VerificationStatus.UNVERIFIED,
                        "Manual / Unmetered",
                        "Live IoT telemetry on primary trunk drains and pump discharge is currently UNAVAILABLE. Network data is unmetered."
                )
        );

        repo.saveAll(defaults);
        log.info("Initialized {} authoritative data sources in Twin Data Source Registry.", defaults.size());
    }

    @Transactional(readOnly = true)
    public List<DataSourceRegistry> getAllSources() {
        return repo.findAll();
    }
}
