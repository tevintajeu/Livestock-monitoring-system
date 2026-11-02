package com.livestock.vetclient;

import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.ProductionRecord;
import com.livestock.vetclient.data.model.TelemetryRecord;
import com.livestock.vetclient.network.Esp32SyncClient;
import com.livestock.vetclient.util.AuthManager;
import com.livestock.vetclient.util.BiometricDiagnosticEngine;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests verifying 5-field & 7-field CSV parsing, anomaly flag mapping (0, 1, 2, 3),
 * human-centered plain-language translations, SHA-256 PIN hashing, and yield insights.
 */
public class HcdOverhaulUnitTest {

    @Test
    public void testFiveFieldWokwiCsvParser() throws Exception {
        Esp32SyncClient client = new Esp32SyncClient();
        String csv = "node_id,temp,acc_x,distance,anomaly_flag\n" +
                     "NODE_001,40.20,1.40,220,1\n" +
                     "NODE_002,38.60,0.80,680,2\n" +
                     "NODE_003,37.40,16.50,150,3\n" +
                     "NODE_004,38.50,0.60,120,0\n";

        List<TelemetryRecord> records = client.parseCsvPayload(csv);
        assertEquals(4, records.size());

        // NODE_001: Fever
        TelemetryRecord r1 = records.get(0);
        assertEquals("NODE_001", r1.getNodeId());
        assertEquals(40.20, r1.getTemperature(), 0.01);
        assertTrue(r1.isFever());
        assertEquals(1, r1.getAnomalyFlag());

        // NODE_002: Straying (>500m)
        TelemetryRecord r2 = records.get(1);
        assertEquals("NODE_002", r2.getNodeId());
        assertTrue(r2.isStray());
        assertEquals(2, r2.getAnomalyFlag());

        // NODE_003: Predator/Panic Motion (>14.0 m/s^2, Flag 3) & Calving Drop (<37.8C)
        TelemetryRecord r3 = records.get(2);
        assertEquals("NODE_003", r3.getNodeId());
        assertTrue(r3.isPredatorOrPanic());
        assertTrue(r3.isCalvingDrop());
        assertEquals(3, r3.getAnomalyFlag());

        // NODE_004: Healthy
        TelemetryRecord r4 = records.get(3);
        assertFalse(r4.needsAttention());
        assertEquals(0, r4.getAnomalyFlag());
    }

    @Test
    public void testSevenFieldLegacyCsvParser() throws Exception {
        Esp32SyncClient client = new Esp32SyncClient();
        String csv = "NODE_001,39.8,1.2,0.5,9.8,1,1727169615\n" +
                     "NODE_002,38.5,0.4,0.2,9.8,310.0,0\n";

        List<TelemetryRecord> records = client.parseCsvPayload(csv);
        assertEquals(2, records.size());
        assertTrue(records.get(0).isFever());
        assertEquals(310.0, records.get(1).getDistance(), 0.01);
    }

    @Test
    public void testHumanCenteredTranslationsAndYieldBanner() {
        AnimalProfile malkia = new AnimalProfile("NODE_001", "Malkia", "KE-042", "Boran", "Milking", "Lead cow");
        assertEquals("Malkia (Tag #KE-042)", malkia.getDisplayHeader());
        assertEquals("Boran • Milking", malkia.getBreedAndStatusSubtitle());

        TelemetryRecord feverRecord = new TelemetryRecord("NODE_001", 40.2, 1.2, 0.0, 0.0, 220.0, 1, System.currentTimeMillis());
        assertTrue(feverRecord.getPlainLanguageSummary("Malkia").contains("Malkia"));
        String banner = BiometricDiagnosticEngine.generateHealthToYieldBanner(feverRecord, malkia);
        assertTrue(banner.contains("Fever detected! Expect temporary milk drop"));

        ProductionRecord prod = new ProductionRecord("NODE_001", System.currentTimeMillis(), 5.5, 4.0, "Normal", "Herder", "");
        assertEquals(9.5, prod.getTotalMilkLiters(), 0.01);
    }

    @Test
    public void testSha256PinHashing() {
        String h1 = AuthManager.sha256("1234");
        String h2 = AuthManager.sha256("1234");
        String hVet = AuthManager.sha256("9999");
        assertEquals(h1, h2);
        assertNotEquals(h1, hVet);
        assertEquals(64, h1.length());
    }
}

