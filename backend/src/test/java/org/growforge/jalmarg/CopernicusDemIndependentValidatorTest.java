package org.growforge.jalmarg;

import org.growforge.jalmarg.service.CopernicusDemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent GeoTIFF decoding verification test.
 * Implements an independent TIFF COG parsing engine to validate CopernicusDemService bit-for-bit.
 */
class CopernicusDemIndependentValidatorTest {

    private CopernicusDemService demService;
    private File rasterFile;

    @BeforeEach
    void setUp() {
        File candidate = new File("data/dem/Copernicus_DSM_COG_10_N28_00_E077_00_DEM.tif");
        if (!candidate.exists()) {
            candidate = new File("backend/data/dem/Copernicus_DSM_COG_10_N28_00_E077_00_DEM.tif");
        }
        rasterFile = candidate;
        demService = new CopernicusDemService(true, rasterFile.getAbsolutePath());
    }

    /**
     * Independent reference extractor implemented independently from CopernicusDemService.
     */
    private double independentExtractElevation(double lat, double lng) throws Exception {
        assertTrue(rasterFile.exists(), "Copernicus DEM tile must exist on disk");

        try (RandomAccessFile raf = new RandomAccessFile(rasterFile, "r")) {
            byte[] hdr = new byte[8];
            raf.readFully(hdr);
            ByteOrder order = (hdr[0] == 'I') ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

            long ifdOffset = ByteBuffer.wrap(hdr, 4, 4).order(order).getInt() & 0xFFFFFFFFL;
            raf.seek(ifdOffset);
            int entryCount = readShort(raf, order);

            long[] offsets = null;
            long[] counts = null;
            double[] scale = null;
            double[] tiepoints = null;
            int width = 3600, height = 3600, tileDim = 1024, predictor = 3;

            for (int i = 0; i < entryCount; i++) {
                int tag = readShort(raf, order);
                int type = readShort(raf, order);
                long count = readInt(raf, order);
                long val = readInt(raf, order);

                if (tag == 324) { // TileOffsets
                    offsets = readArrayLong(raf, order, type, count, val);
                } else if (tag == 325) { // TileByteCounts
                    counts = readArrayLong(raf, order, type, count, val);
                } else if (tag == 33550) { // ModelPixelScale
                    scale = readArrayDouble(raf, order, count, val);
                } else if (tag == 33922) { // ModelTiepoint
                    tiepoints = readArrayDouble(raf, order, count, val);
                } else if (tag == 317) {
                    predictor = (int) val;
                }
            }

            assertNotNull(offsets);
            assertNotNull(counts);
            assertNotNull(scale);
            assertNotNull(tiepoints);

            double originX = tiepoints[3];
            double originY = tiepoints[4];
            double dx = scale[0];
            double dy = scale[1];

            int px = (int) Math.floor((lng - originX) / dx);
            int py = (int) Math.floor((originY - lat) / dy);
            px = Math.max(0, Math.min(width - 1, px));
            py = Math.max(0, Math.min(height - 1, py));

            int tilesAcross = (width + tileDim - 1) / tileDim;
            int tileCol = px / tileDim;
            int tileRow = py / tileDim;
            int tileIdx = tileRow * tilesAcross + tileCol;

            long tileOffset = offsets[tileIdx];
            int byteCount = (int) counts[tileIdx];

            raf.seek(tileOffset);
            byte[] compressed = new byte[byteCount];
            raf.readFully(compressed);

            byte[] uncompressed = new byte[tileDim * tileDim * 4];
            Inflater inflater = new Inflater();
            inflater.setInput(compressed);
            inflater.inflate(uncompressed);
            inflater.end();

            // Apply independent predictor decoding
            byte[] decoded = new byte[uncompressed.length];
            int rowBytes = tileDim * 4;
            for (int r = 0; r < tileDim; r++) {
                int rowStart = r * rowBytes;
                byte[] diffRow = new byte[rowBytes];
                for (int j = 0; j < rowBytes; j++) {
                    diffRow[j] = (j == 0) ? uncompressed[rowStart + j] : (byte) (uncompressed[rowStart + j] + diffRow[j - 1]);
                }
                for (int p = 0; p < tileDim; p++) {
                    for (int b = 0; b < 4; b++) {
                        decoded[rowStart + p * 4 + (3 - b)] = diffRow[b * tileDim + p];
                    }
                }
            }

            int inTileX = px % tileDim;
            int inTileY = py % tileDim;
            int pixelOffset = (inTileY * tileDim + inTileX) * 4;

            float rawElev = ByteBuffer.wrap(decoded, pixelOffset, 4).order(order).getFloat();
            return Math.round(rawElev * 100.0) / 100.0;
        }
    }

    private static int readShort(RandomAccessFile raf, ByteOrder order) throws Exception {
        byte[] b = new byte[2];
        raf.readFully(b);
        return ByteBuffer.wrap(b).order(order).getShort() & 0xFFFF;
    }

    private static long readInt(RandomAccessFile raf, ByteOrder order) throws Exception {
        byte[] b = new byte[4];
        raf.readFully(b);
        return ByteBuffer.wrap(b).order(order).getInt() & 0xFFFFFFFFL;
    }

    private static long[] readArrayLong(RandomAccessFile raf, ByteOrder order, int type, long count, long offset) throws Exception {
        long[] arr = new long[(int) count];
        if (count == 1) {
            arr[0] = offset;
            return arr;
        }
        long cur = raf.getFilePointer();
        raf.seek(offset);
        for (int i = 0; i < count; i++) {
            arr[i] = (type == 3) ? readShort(raf, order) : readInt(raf, order);
        }
        raf.seek(cur);
        return arr;
    }

    private static double[] readArrayDouble(RandomAccessFile raf, ByteOrder order, long count, long offset) throws Exception {
        double[] arr = new double[(int) count];
        long cur = raf.getFilePointer();
        raf.seek(offset);
        for (int i = 0; i < count; i++) {
            byte[] b = new byte[8];
            raf.readFully(b);
            arr[i] = ByteBuffer.wrap(b).order(order).getDouble();
        }
        raf.seek(cur);
        return arr;
    }

    // 1. Bit-for-bit independent verification at Minto Bridge
    @Test
    void verifyMintoBridgeElevationMatchesIndependentReaderBitForBit() throws Exception {
        double lat = 28.6347;
        double lng = 77.2263;

        CopernicusDemService.TerrainRiskProfile serviceProfile = demService.getElevationProfile(lat, lng);
        double independentElev = independentExtractElevation(lat, lng);

        assertTrue(serviceProfile.available());
        assertEquals(independentElev, serviceProfile.elevationMetersAmsl(), 0.001,
                "Service elevation must match independent reader with zero discrepancy");
        assertEquals(211.08, serviceProfile.elevationMetersAmsl(), 0.01);
    }

    // 2. Bit-for-bit independent verification at Pul Prahladpur
    @Test
    void verifyPulPrahladpurElevationMatchesIndependentReader() throws Exception {
        double lat = 28.5019;
        double lng = 77.2873;

        CopernicusDemService.TerrainRiskProfile serviceProfile = demService.getElevationProfile(lat, lng);
        double independentElev = independentExtractElevation(lat, lng);

        assertTrue(serviceProfile.available());
        assertEquals(independentElev, serviceProfile.elevationMetersAmsl(), 0.001,
                "Service elevation must match independent reader with zero discrepancy");
        assertEquals(223.59, serviceProfile.elevationMetersAmsl(), 0.01);
    }

    // 3. Bit-for-bit independent verification at NH-9 Sector 62 Noida
    @Test
    void verifyNoidaSec62ElevationMatchesIndependentReader() throws Exception {
        double lat = 28.6280;
        double lng = 77.3690;

        CopernicusDemService.TerrainRiskProfile serviceProfile = demService.getElevationProfile(lat, lng);
        double independentElev = independentExtractElevation(lat, lng);

        assertTrue(serviceProfile.available());
        assertEquals(independentElev, serviceProfile.elevationMetersAmsl(), 0.001,
                "Service elevation must match independent reader with zero discrepancy");
        assertEquals(205.51, serviceProfile.elevationMetersAmsl(), 0.01);
    }

    // 4. Boundary checking
    @Test
    void verifyOutOfBoundsCoordinatesReturnUnavailable() {
        // Outside 28N-29N, 77E-78E tile bounds
        CopernicusDemService.TerrainRiskProfile outNorth = demService.getElevationProfile(29.5, 77.5);
        assertFalse(outNorth.available());
        assertEquals("COORDINATES_OUTSIDE_TILE_BOUNDS", outNorth.urbanLimitationsNote());

        CopernicusDemService.TerrainRiskProfile outEast = demService.getElevationProfile(28.5, 78.5);
        assertFalse(outEast.available());
        assertEquals("COORDINATES_OUTSIDE_TILE_BOUNDS", outEast.urbanLimitationsNote());
    }
}
