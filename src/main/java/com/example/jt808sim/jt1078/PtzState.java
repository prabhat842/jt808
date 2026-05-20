package com.example.jt808sim.jt1078;

/**
 * Per-channel PTZ/lens state updated by 0x9301–0x9306 head control commands (§5.7, JT/T 1078-2016).
 */
public class PtzState {
    // Tilt: 0 = full down, 1799 = full up (tenths of degrees, 0–179.9°)
    private int tiltTenthDegrees = 900;
    // Pan: 0–3599 (tenths of degrees, 0–359.9°), wraps
    private int panTenthDegrees = 0;
    // Focal length abstract units 0–255 (0x9302)
    private int focalLength = 128;
    // Aperture abstract units 0–255 (0x9303)
    private int aperture = 128;
    // Zoom abstract units 0–255 (0x9306)
    private int zoom = 0;
    // Wiper on/off (0x9304)
    private boolean wiperOn = false;
    // Infrared fill light on/off (0x9305)
    private boolean infraredOn = false;

    /** Apply 0x9301 head rotation. direction: 0=stop 1=up 2=down 3=left 4=right, speed 0–255. */
    public synchronized void applyRotation(int direction, int speed) {
        int step = Math.max(1, speed);
        switch (direction) {
            case 1 -> tiltTenthDegrees = clamp(tiltTenthDegrees + step, 0, 1799);
            case 2 -> tiltTenthDegrees = clamp(tiltTenthDegrees - step, 0, 1799);
            case 3 -> panTenthDegrees = (panTenthDegrees - step + 3600) % 3600;
            case 4 -> panTenthDegrees = (panTenthDegrees + step) % 3600;
            default -> { }
        }
    }

    /** Apply 0x9302 focus control. direction: 0=increase focal length, 1=decrease. */
    public synchronized void applyFocus(int direction) {
        focalLength = clamp(direction == 0 ? focalLength + 5 : focalLength - 5, 0, 255);
    }

    /** Apply 0x9303 aperture control. method: 0=open wider, 1=close down. */
    public synchronized void applyAperture(int method) {
        aperture = clamp(method == 0 ? aperture + 5 : aperture - 5, 0, 255);
    }

    /** Apply 0x9306 zoom (variable) control. direction: 0=zoom in (up), 1=zoom out (down). */
    public synchronized void applyZoom(int direction) {
        zoom = clamp(direction == 0 ? zoom + 5 : zoom - 5, 0, 255);
    }

    /** Set wiper state from 0x9304. value: 0=stop, 1=start. */
    public synchronized void setWiper(boolean on)    { wiperOn = on; }

    /** Set infrared fill light state from 0x9305. value: 0=stop, 1=start. */
    public synchronized void setInfrared(boolean on) { infraredOn = on; }

    public synchronized int tiltTenthDegrees()  { return tiltTenthDegrees; }
    public synchronized int panTenthDegrees()   { return panTenthDegrees; }
    public synchronized int focalLength()       { return focalLength; }
    public synchronized int aperture()          { return aperture; }
    public synchronized int zoom()              { return zoom; }
    public synchronized boolean wiperOn()       { return wiperOn; }
    public synchronized boolean infraredOn()    { return infraredOn; }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
