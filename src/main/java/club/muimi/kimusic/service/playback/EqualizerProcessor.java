package club.muimi.kimusic.service.playback;

import club.muimi.kimusic.model.EqualizerSettings;

import java.util.Arrays;

final class EqualizerProcessor {
    private static final double QUALITY = 1.0;
    private EqualizerSettings settings = EqualizerSettings.defaults();
    private double sampleRate;
    private int channels;
    private double compensation = 1;
    private Biquad[][] filters = new Biquad[0][0];

    void process(byte[] buffer, int length, int channelCount, double rate,
                 EqualizerSettings requested) {
        if (buffer == null || length < 2 || channelCount <= 0 || !Double.isFinite(rate) || rate <= 0) return;
        EqualizerSettings next = requested == null ? EqualizerSettings.defaults() : requested;
        if (next.isEnabled() && (rate != sampleRate || channelCount != channels || !sameSettings(next, settings))) {
            rebuild(next, rate, channelCount);
        } else if (!next.isEnabled() && settings.isEnabled()) {
            settings = next.copy();
            filters = new Biquad[0][0];
        }
        if (!next.isEnabled() || filters.length == 0) return;
        int frameSize = channelCount * 2;
        int frames = length / frameSize;
        for (int frame = 0; frame < frames; frame++) {
            int frameOffset = frame * frameSize;
            for (int channel = 0; channel < channelCount; channel++) {
                int offset = frameOffset + channel * 2;
                double value = ((short) ((buffer[offset] & 0xff) | (buffer[offset + 1] << 8))) / 32768.0;
                for (Biquad filter : filters[channel]) value = filter.process(value);
                value *= compensation;
                value = limitPeak(value);
                int sample = (int) Math.round(Math.max(-1, Math.min(0.999969, value)) * 32768.0);
                buffer[offset] = (byte) sample;
                buffer[offset + 1] = (byte) (sample >>> 8);
            }
        }
    }

    private void rebuild(EqualizerSettings next, double rate, int channelCount) {
        boolean reuseFilters = sampleRate == rate && channels == channelCount
                && filters.length == channelCount
                && channelCount > 0 && filters[0].length == EqualizerSettings.BAND_COUNT;
        settings = next.copy(); sampleRate = rate; channels = channelCount;
        double[] gains = settings.getGains();
        if (!reuseFilters) {
            filters = new Biquad[channelCount][EqualizerSettings.BAND_COUNT];
        }
        for (int channel = 0; channel < channelCount; channel++) for (int band = 0; band < EqualizerSettings.BAND_COUNT; band++) {
            if (reuseFilters) {
                filters[channel][band].setPeaking(EqualizerSettings.BAND_FREQUENCIES[band], rate,
                        gains[band], QUALITY);
            } else {
                filters[channel][band] = Biquad.peaking(EqualizerSettings.BAND_FREQUENCIES[band], rate,
                        gains[band], QUALITY);
            }
        }
        double maximumResponse = 1;
        double highestFrequency = Math.min(rate * 0.45, 20_000);
        for (int index = 0; index < 512; index++) {
            double frequency = 20 * Math.pow(Math.max(20, highestFrequency) / 20, index / 511.0);
            double response = 1;
            for (Biquad filter : filters[0]) response *= filter.magnitudeAt(frequency, rate);
            maximumResponse = Math.max(maximumResponse, response);
        }
        // Leave one dB of headroom beyond a measured boost, without attenuating Flat.
        double maximumBoostDb = 20 * Math.log10(maximumResponse);
        compensation = maximumBoostDb > 0.01
                ? Math.pow(10, -(maximumBoostDb + 1) / 20.0) : 1;
    }

    private double limitPeak(double value) {
        double magnitude = Math.abs(value);
        if (magnitude <= 0.94) return value;
        double softened = 0.94 + 0.03 * Math.tanh((magnitude - 0.94) / 0.03);
        return Math.copySign(softened, value);
    }

    private boolean sameSettings(EqualizerSettings left, EqualizerSettings right) {
        return left.isEnabled() == right.isEnabled() && Arrays.equals(left.getGains(), right.getGains());
    }
    private static final class Biquad {
        private double b0, b1, b2, a1, a2;
        private double z1, z2;
        private Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0; this.b1 = b1; this.b2 = b2; this.a1 = a1; this.a2 = a2;
        }
        static Biquad peaking(double frequency, double sampleRate, double gain, double quality) {
            double a = Math.pow(10, gain / 40.0);
            double omega = 2 * Math.PI * Math.min(frequency, sampleRate * 0.45) / sampleRate;
            double alpha = Math.sin(omega) / (2 * quality), cos = Math.cos(omega);
            double a0 = 1 + alpha / a;
            return new Biquad((1 + alpha * a) / a0, (-2 * cos) / a0,
                    (1 - alpha * a) / a0, (-2 * cos) / a0, (1 - alpha / a) / a0);
        }
        void setPeaking(double frequency, double sampleRate, double gain, double quality) {
            double a = Math.pow(10, gain / 40.0);
            double omega = 2 * Math.PI * Math.min(frequency, sampleRate * 0.45) / sampleRate;
            double alpha = Math.sin(omega) / (2 * quality), cos = Math.cos(omega);
            double a0 = 1 + alpha / a;
            b0 = (1 + alpha * a) / a0;
            b1 = (-2 * cos) / a0;
            b2 = (1 - alpha * a) / a0;
            a1 = (-2 * cos) / a0;
            a2 = (1 - alpha / a) / a0;
        }
        double process(double input) {
            double output = b0 * input + z1;
            z1 = b1 * input - a1 * output + z2;
            z2 = b2 * input - a2 * output;
            return output;
        }
        double magnitudeAt(double frequency, double sampleRate) {
            double omega = 2 * Math.PI * frequency / sampleRate;
            double cos1 = Math.cos(omega);
            double sin1 = -Math.sin(omega);
            double cos2 = Math.cos(2 * omega);
            double sin2 = -Math.sin(2 * omega);
            double numeratorReal = b0 + b1 * cos1 + b2 * cos2;
            double numeratorImaginary = b1 * sin1 + b2 * sin2;
            double denominatorReal = 1 + a1 * cos1 + a2 * cos2;
            double denominatorImaginary = a1 * sin1 + a2 * sin2;
            double denominator = Math.hypot(denominatorReal, denominatorImaginary);
            return denominator > 1e-12
                    ? Math.hypot(numeratorReal, numeratorImaginary) / denominator : 1;
        }
    }
}
