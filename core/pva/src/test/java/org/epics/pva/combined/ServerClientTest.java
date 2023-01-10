/*
 * Copyright (C) 2023 European Spallation Source ERIC.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package org.epics.pva.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.epics.pva.client.MonitorListener;
import org.epics.pva.client.PVAClient;
import org.epics.pva.data.PVAByte;
import org.epics.pva.data.PVAByteArray;
import org.epics.pva.data.PVAData;
import org.epics.pva.data.PVADouble;
import org.epics.pva.data.PVADoubleArray;
import org.epics.pva.data.PVAFloat;
import org.epics.pva.data.PVAFloatArray;
import org.epics.pva.data.PVAInt;
import org.epics.pva.data.PVAIntArray;
import org.epics.pva.data.PVAShort;
import org.epics.pva.data.PVAShortArray;
import org.epics.pva.data.PVAString;
import org.epics.pva.data.PVAStringArray;
import org.epics.pva.data.PVAStructure;
import org.epics.pva.data.PVAStructures;
import org.epics.pva.data.nt.FakeDataUtil;
import org.epics.pva.data.nt.PVAAlarm;
import org.epics.pva.data.nt.PVAControl;
import org.epics.pva.data.nt.PVADisplay;
import org.epics.pva.data.nt.PVAScalar;
import org.epics.pva.data.nt.PVAScalarDescriptionNameException;
import org.epics.pva.data.nt.PVAScalarValueNameException;
import org.epics.pva.data.nt.PVATimeStamp;
import org.epics.pva.data.nt.PVADisplay.Form;
import org.epics.pva.server.PVAServer;
import org.epics.pva.server.ServerPV;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ServerClientTest {

    private PVAServer server;
    private PVAClient client;

    @BeforeEach
    public void setUp() throws Exception {
        server = new PVAServer();
        client = new PVAClient();
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.close();
        client.close();

        // Wait for closes to finish
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Provides the input data for the test cases.
     * 
     * First goes over every scalar type, converting the first array of the
     * generated fake data to the PVAccess type.
     * Seconds goes over every waveform type, converting each array of the
     * generated fake data to the PVAccess type.
     * 
     * @return input data
     */
    public static Collection<Object[]> data() {
        List<List<Double>> fakeData = FakeDataUtil.fakeData(100, 1.1, 10);
        return Arrays.asList(new Object[][] {
                {
                        fakeData.get(0).stream().map((d) -> new PVAString(PVAScalar.VALUE_NAME_STRING, d.toString()))
                                .toList() },
                {
                        fakeData.get(0).stream().map(Double::shortValue)
                                .map((s) -> new PVAShort(PVAScalar.VALUE_NAME_STRING, false, s)).toList() },
                {
                        fakeData.get(0).stream().map(Double::floatValue)
                                .map((f) -> new PVAFloat(PVAScalar.VALUE_NAME_STRING, f)).toList() },
                {
                        fakeData.get(0).stream().map(Double::byteValue)
                                .map((b) -> new PVAByte(PVAScalar.VALUE_NAME_STRING, false, b)).toList() },
                {
                        fakeData.get(0).stream().map(Double::intValue)
                                .map((i) -> new PVAInt(PVAScalar.VALUE_NAME_STRING, false, i)).toList() },
                {
                        fakeData.get(0).stream().map(Double::doubleValue)
                                .map((d) -> new PVADouble(PVAScalar.VALUE_NAME_STRING, d)).toList() },
                {
                        fakeData.stream()
                                .map((dArray) -> new PVAStringArray(PVAScalar.VALUE_NAME_STRING,
                                        dArray.stream().map((d) -> d.toString()).toArray(String[]::new)))
                                .toList() },
                {
                        fakeData.stream()
                                .map((dArray) -> {
                                    short[] array = new short[dArray.size()];
                                    int count = 0;
                                    for (Double d : dArray) {
                                        array[count] = d.shortValue();
                                        count++;
                                    }
                                    return new PVAShortArray(PVAScalar.VALUE_NAME_STRING, false, array);
                                }).toList() },
                {
                        fakeData.stream()
                                .map((dArray) -> {
                                    float[] array = new float[dArray.size()];
                                    int count = 0;
                                    for (Double d : dArray) {
                                        array[count] = d.floatValue();
                                        count++;
                                    }
                                    return new PVAFloatArray(PVAScalar.VALUE_NAME_STRING, array);
                                }).toList() },
                {
                        fakeData.stream()
                                .map((dArray) -> {
                                    byte[] array = new byte[dArray.size()];
                                    int count = 0;
                                    for (Double d : dArray) {
                                        array[count] = d.byteValue();
                                        count++;
                                    }
                                    return new PVAByteArray(PVAScalar.VALUE_NAME_STRING, false, array);
                                }).toList() },
                {
                        fakeData.stream()
                                .map((dArray) -> new PVAIntArray(PVAScalar.VALUE_NAME_STRING, false,
                                        dArray.stream().mapToInt((d) -> d.intValue()).toArray()))
                                .toList() },
                {
                        fakeData.stream().map((dArray) -> new PVADoubleArray(PVAScalar.VALUE_NAME_STRING,
                                dArray.stream().mapToDouble((d) -> d.doubleValue()).toArray()))
                                .toList() },
        });
    }

    static PVAStructure buildPVAStructure(String pvName, Instant instant, PVAData value, String pvDescription) {
        PVAScalar.Builder<PVAData> builder = new PVAScalar.Builder<>();
        builder.name(pvName);
        builder.value(value);
        builder.description(new PVAString("description",
                pvDescription));
        builder.alarm(new PVAAlarm(1, 2,
                pvDescription + "alarm message"));
        builder.timeStamp(new PVATimeStamp(instant));
        builder.display(new PVADisplay(0, 1, pvDescription + "display", "units", 4, Form.STRING));
        builder.control(new PVAControl(0, 1, 1));
        try {
            return builder.build();
        } catch (PVAScalarValueNameException e) {
            e.printStackTrace();
            fail();
        } catch (PVAScalarDescriptionNameException e) {
            e.printStackTrace();
            fail();
        }
        return null;
    }

    /**
     * Test for setting up a pv in a server with all data structures.
     * Then sending some fake data.
     * Then in a client receiving the data.
     * Then assert sent and received data is the same.
     */
    @ParameterizedTest
    @MethodSource("data")
    public <S extends PVAData> void testSinglePV(List<S> inputData) {
        String pvName = "PV:" + inputData.get(0).getClass().getSimpleName() + ":" + UUID.randomUUID().toString();

        var fakeData = inputData.get(0);
        String pvDescription = fakeData.getClass().getSimpleName() + ServerClientTest.class.getName() + " test on "
                + pvName;
        Instant instant = Instant.now();
        var instants = new ArrayList<>();
        instants.add(instant);
        PVAStructure testPV = buildPVAStructure(pvName, Instant.now(), fakeData, pvDescription);
        ServerPV serverPV = server.createPV(pvName, testPV);

        var ref = new AtomicReference<HashMap<Instant, PVAData>>();
        ref.set(new HashMap<>());
        MonitorListener listener = (ch, changes, overruns, data) -> {
            System.out.println("Got data " + data.get(PVAScalar.VALUE_NAME_STRING));
            ref.getAndUpdate((l) -> {
                Instant recInstant = PVAStructures.getTime(data.get(PVATimeStamp.TIMESTAMP_NAME_STRING));
                PVAData recData = data.get(PVAScalar.VALUE_NAME_STRING);
                l.put(recInstant, recData);
                return l;
            });
        };

        var channel = client.getChannel(pvName);
        try {
            channel.connect().get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        try {
            channel.subscribe(pvDescription, listener);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        var sentData = new HashMap<Instant, PVAData>();
        for (S input : inputData) {
            S newValue = testPV.get(PVAScalar.VALUE_NAME_STRING);
            try {
                newValue.setValue(input);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
            PVATimeStamp timeStamp = testPV.get(PVATimeStamp.TIMESTAMP_NAME_STRING);
            instant = Instant.now();
            instants.add(instant);
            timeStamp.set(instant);
            sentData.put(instant, newValue);
            try {
                serverPV.update(testPV);
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
            try {
                // Sleep to allow time for client to receive requests
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
            System.out.println("Sent data " + testPV.get(PVAScalar.VALUE_NAME_STRING));
        }

        serverPV.close();
        channel.close();

        assertEquals(inputData.size(), ref.get().size());
        assertEquals(sentData, ref.get());
    }

}
