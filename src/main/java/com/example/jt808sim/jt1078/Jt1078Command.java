package com.example.jt808sim.jt1078;

public sealed interface Jt1078Command permits
        Jt1078Command.QueryAudioVideoAttributes,
        Jt1078Command.RealTimeRequest,
        Jt1078Command.RealTimeControl,
        Jt1078Command.QueryResourceList,
        Jt1078Command.PlaybackRequest,
        Jt1078Command.PlaybackControl,
        Jt1078Command.FileUploadCommand,
        Jt1078Command.FileUploadControl,
        Jt1078Command.PtzControl,
        Jt1078Command.SimpleChannelControl {

    record QueryAudioVideoAttributes() implements Jt1078Command {
    }

    record RealTimeRequest(String host, int tcpPort, int udpPort, int channel, int dataType, int streamType) implements Jt1078Command {
        public enum Mode {
            LIVE_AUDIO_VIDEO,
            LIVE_VIDEO,
            TALK,
            LISTEN,
            BROADCAST,
            PASSTHROUGH,
            UNKNOWN
        }

        public int preferredPort() {
            return tcpPort > 0 ? tcpPort : udpPort;
        }

        public Mode mode() {
            return switch (dataType) {
                case 0 -> Mode.LIVE_AUDIO_VIDEO;
                case 1 -> Mode.LIVE_VIDEO;
                case 2 -> Mode.TALK;
                case 3 -> Mode.LISTEN;
                case 4 -> Mode.BROADCAST;
                case 5 -> Mode.PASSTHROUGH;
                default -> Mode.UNKNOWN;
            };
        }
    }

    record RealTimeControl(int channel, int command, int closeType, int streamType) implements Jt1078Command {
    }

    record QueryResourceList(int channel, byte[] startTime, byte[] endTime, long alarmHigh, long alarmLow, int resourceType, int streamType, int storageType) implements Jt1078Command {
    }

    record PlaybackRequest(String host, int tcpPort, int udpPort, int channel, int audioVideoType, int streamType, int storageType, int playbackMode, int playbackSpeed, byte[] startTime, byte[] endTime) implements Jt1078Command {
        public int preferredPort() {
            return tcpPort > 0 ? tcpPort : udpPort;
        }
    }

    record PlaybackControl(int channel, int command, int speed, byte[] playbackPosition) implements Jt1078Command {
    }

    record FileUploadCommand(String host, int port, String username, String password, String path, int channel, byte[] startTime, byte[] endTime, long alarmHigh, long alarmLow, int resourceType, int streamType, int storageType, int conditions) implements Jt1078Command {
    }

    record FileUploadControl(int responseSequence, int control) implements Jt1078Command {
    }

    record PtzControl(int channel, int direction, int speed) implements Jt1078Command {
    }

    record SimpleChannelControl(int messageId, int channel, int value) implements Jt1078Command {
    }
}
