// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.writer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.opendatahub.timeseries.bdp.dto.dto.DataMapDto;
import com.opendatahub.timeseries.bdp.dto.dto.RecordDtoImpl;
import com.opendatahub.timeseries.bdp.dto.dto.SimpleRecordDto;
import com.opendatahub.timeseries.bdp.writer.dal.DataType;
import com.opendatahub.timeseries.bdp.writer.dal.Measurement;
import com.opendatahub.timeseries.bdp.writer.dal.Partition;
import com.opendatahub.timeseries.bdp.writer.dal.Provenance;
import com.opendatahub.timeseries.bdp.writer.dal.Station;
import com.opendatahub.timeseries.bdp.writer.dal.TimeSeries;
import com.opendatahub.timeseries.bdp.writer.writer.DataManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

/**
 * Setup of the writer test cases with initial data, that will be added and
 * removed for each test.
 *
 * Abstract, because we do not want to run this class itself.
 */
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
})
@DirtiesContext
public abstract class WriterSetupTest extends AbstractJUnit4SpringContextTests {

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    DataManager dataManager;

    protected static final String STATION_TYPE = "Environment";

    protected EntityManager em;
    protected Station station;
    protected DataType type;
    protected Measurement measurement;
    protected Measurement measurementOld;
    protected Provenance provenance;
    protected Partition partition;

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.5-alpine").asCompatibleSubstituteFor("postgres"));

    @BeforeAll
    static void startPG() {
        // temp workaround until testcontainers is fixed
        // https://github.com/testcontainers/testcontainers-java/issues/11212#issuecomment-3516573631
        System.setProperty("api.version", "1.44");
        postgres.start();
    }

    @AfterAll
    static void stopPG() {
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?currentSchema=intimev2,public");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    public void setup() {

        // Make sure no remainders left after last run
        cleanup();

        em = entityManagerFactory.createEntityManager();
        partition = Partition.getDefault(em);

        station = new Station(STATION_TYPE, "Station01", "Station One");
        type = new DataType("NO2", "mg", "Fake type", "Instants");
        Date today = new Date();
        TimeSeries ts = new TimeSeries(station, type, 500, TimeSeries.ValueTable.NUMBER);
        measurement = new Measurement();
        measurement.setTimeseries(ts);
        measurement.setValue(1.11);
        measurement.setTimestamp(today);
        measurementOld = new Measurement();
        measurementOld.setTimeseries(ts);
        measurementOld.setValue(2.22);
        measurementOld.setTimestamp(new Date(today.getTime() - 1000));
        provenance = new Provenance();
        provenance.setDataCollector("writer-integration-tests");
        provenance.setDataCollectorVersion("0.0.0");
        provenance.setLineage("from-the-writer-integration-tests");
        provenance.setUuid("12345678");

        List<RecordDtoImpl> values = new ArrayList<>();
        values.add(new SimpleRecordDto(measurement.getTimestamp().getTime(), measurement.getValue(),
                measurement.getTimeseries().getPeriod()));
        values.add(new SimpleRecordDto(measurementOld.getTimestamp().getTime(), measurementOld.getValue(),
                measurementOld.getTimeseries().getPeriod()));

        try {
            em.getTransaction().begin();
            em.persist(station);
            em.persist(type);
            em.persist(provenance);
            em.getTransaction().commit();
            dataManager.pushRecords(
                    STATION_TYPE,
                    null,
                    DataMapDto.build(provenance.getUuid(), station.getStationcode(), type.getCname(), values));
        } catch (Exception e) {
            em.getTransaction().rollback();
            if (em.isOpen()) {
                em.clear();
                em.close();
            }
            throw e;
        }
    }

    @AfterEach
    public void cleanup() {
        em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();

            // Delete all measurements (all types)
            em.createQuery("DELETE FROM Measurement").executeUpdate();
            em.createQuery("DELETE FROM MeasurementHistory").executeUpdate();
            em.createQuery("DELETE FROM MeasurementString").executeUpdate();
            em.createQuery("DELETE FROM MeasurementStringHistory").executeUpdate();
            em.createQuery("DELETE FROM MeasurementJSON").executeUpdate();
            em.createQuery("DELETE FROM MeasurementJSONHistory").executeUpdate();
            em.createQuery("DELETE FROM TimeSeries").executeUpdate();
            em.createQuery("DELETE FROM PartitionDef").executeUpdate();
            em.createQuery("DELETE FROM Event").executeUpdate();
            em.createQuery("UPDATE Station SET metaData = NULL").executeUpdate();
            em.createQuery("DELETE FROM MetaData").executeUpdate();
            em.createQuery("DELETE FROM Station").executeUpdate();
            em.createQuery("DELETE FROM DataType").executeUpdate();
            em.createQuery("DELETE FROM Provenance").executeUpdate();

            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        } finally {
            if (em.isOpen()) {
                em.clear();
                em.close();
            }
        }
    }
}
