package elucent.albedo.lighting;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Light {
    public float x;
    public float y;
    public float z;
    public float r;
    public float g;
    public float b;
    public float a;
    public float rx;
    public float ry;
    public float rz;
    public float angle;

    /**
     * Creates an omnidirectional point light of the given radius.
     * <p>
     * The angle is a full sphere: the shader's cone attenuation divides by it, so leaving it
     * at zero collapses the light to zero intensity and it renders nothing at all. This
     * matches what {@link Builder#radius(float)} produces.
     */
    public Light(float x, float y, float z, float r, float g, float b, float a, float radius) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.rx = 0.0f;
        this.ry = radius;
        this.rz = 0.0f;
        this.angle = (float) Math.PI * 2;
    }

    public Light(float x, float y, float z, float r, float g, float b, float a, float rx, float ry, float rz, float angle) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.angle = angle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float radius() {
        return (float) Math.sqrt(this.rx * this.rx + this.ry * this.ry + this.rz * this.rz);
    }

    public static final class Builder {
        private float x = Float.NaN;
        private float y = Float.NaN;
        private float z = Float.NaN;
        private float r = Float.NaN;
        private float g = Float.NaN;
        private float b = Float.NaN;
        private float a = Float.NaN;
        private float rx = Float.NaN;
        private float ry = Float.NaN;
        private float rz = Float.NaN;
        private float angle = Float.NaN;

        public Builder pos(BlockPos pos) {
            return this.pos((float) pos.getX() + 0.5f, (float) pos.getY() + 0.5f, (float) pos.getZ() + 0.5f);
        }

        public Builder pos(Vec3d pos) {
            return this.pos(pos.x, pos.y, pos.z);
        }

        public Builder pos(Entity e) {
            return this.pos(e.posX, e.posY, e.posZ);
        }

        public Builder pos(double x, double y, double z) {
            return this.pos((float) x, (float) y, (float) z);
        }

        public Builder pos(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder color(int c, boolean hasAlpha) {
            return this.color(this.extract(c, 2), this.extract(c, 1), this.extract(c, 0), hasAlpha ? this.extract(c, 3) : 1.0f);
        }

        private float extract(int i, int idx) {
            return (float) (i >> idx * 8 & 0xFF) / 255.0f;
        }

        public Builder color(float r, float g, float b) {
            return this.color(r, g, b, 1.0f);
        }

        public Builder color(float r, float g, float b, float a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            return this;
        }

        public Builder radius(float radius) {
            this.rx = 0.0f;
            this.ry = radius;
            this.rz = 0.0f;
            this.angle = (float) Math.PI * 2;
            return this;
        }

        public Builder direction(float x, float y, float z, float angle) {
            this.rx = x;
            this.ry = y;
            this.rz = z;
            this.angle = angle;
            return this;
        }

        public Builder direction(Vec3d vec, float angle) {
            this.rx = (float) vec.x;
            this.ry = (float) vec.y;
            this.rz = (float) vec.z;
            this.angle = angle;
            return this;
        }

        public Light build() {
            if (Float.isFinite(this.x) && Float.isFinite(this.y) && Float.isFinite(this.z)
                    && Float.isFinite(this.r) && Float.isFinite(this.g) && Float.isFinite(this.b) && Float.isFinite(this.a)
                    && Float.isFinite(this.rx) && Float.isFinite(this.ry) && Float.isFinite(this.rz) && Float.isFinite(this.angle)) {
                return new Light(this.x, this.y, this.z, this.r, this.g, this.b, this.a, this.rx, this.ry, this.rz, this.angle);
            }
            throw new IllegalArgumentException("Position, color, and radius must be set, and cannot be infinite");
        }
    }
}
