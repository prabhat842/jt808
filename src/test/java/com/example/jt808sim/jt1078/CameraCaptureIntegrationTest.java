package com.example.jt808sim.jt1078;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CameraCaptureIntegrationTest {
    @Test
    void createsAudioOnlySourceWhenCameraModeDisablesVideoCapture() {
        Jt1078MediaConfig config = cameraConfig(false, true, "lavfi:testsrc=size=640x360:rate=25", "lavfi:anullsrc=r=8000:cl=mono");

        Jt1078FrameSource source = Jt1078MediaSession.createSource(
                config,
                new Jt1078SessionRequest("127.0.0.1", 1078, 1, true, true, false, false, Jt1078FrameType.VIDEO_P, 0, 1, 0, Long.MAX_VALUE, null, null));

        assertInstanceOf(FfmpegAudioFrameSource.class, source);
    }

    @Test
    void createsVideoOnlySourceWhenCameraModeDisablesAudioCapture() {
        Jt1078MediaConfig config = cameraConfig(true, false, "lavfi:testsrc=size=640x360:rate=25", "lavfi:anullsrc=r=8000:cl=mono");

        Jt1078FrameSource source = Jt1078MediaSession.createSource(
                config,
                new Jt1078SessionRequest("127.0.0.1", 1078, 1, true, true, false, false, Jt1078FrameType.VIDEO_P, 0, 1, 0, Long.MAX_VALUE, null, null));

        assertInstanceOf(FfmpegCameraFrameSource.class, source);
    }

    @Test
    void cameraCommandSupportsLavfiVideoSource() {
        List<String> command = FfmpegCameraFrameSource.buildCommand(cameraConfig(true, false, "lavfi:testsrc=size=640x360:rate=25", "default").capture());

        assertEquals(List.of(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "warning",
                "-f", "lavfi",
                "-i", "testsrc=size=640x360:rate=25"), command.subList(0, 8));
    }

    @Test
    void audioCommandSupportsLavfiAudioSource() {
        List<String> command = FfmpegAudioFrameSource.buildCommand(cameraConfig(false, true, "/dev/video0", "lavfi:anullsrc=r=8000:cl=mono").capture());

        assertEquals(List.of(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "warning",
                "-f", "lavfi",
                "-i", "anullsrc=r=8000:cl=mono"), command.subList(0, 8));
    }

    private static Jt1078MediaConfig cameraConfig(boolean videoEnabled, boolean audioEnabled, String videoDevice, String audioDevice) {
        return new Jt1078MediaConfig(
                "127.0.0.1",
                1078,
                "camera",
                List.of(),
                950,
                25,
                new Jt1078MediaConfig.CaptureConfig(
                        videoEnabled,
                        audioEnabled,
                        videoDevice,
                        audioDevice,
                        640,
                        360,
                        25,
                        1200,
                        8000,
                        1,
                        32,
                        "ffmpeg"),
                new Jt1078MediaConfig.TalkConfig(true, false, false, "tmp/jt1078-downlink", 200));
    }
}
