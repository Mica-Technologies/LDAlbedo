package elucent.albedo.lighting;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Light {
    /**
     * Position as a 32-bit float.
     *
     * <p>Public API, so it stays. Be aware that past 2^23 blocks from the origin a float cannot
     * hold a block coordinate exactly and these snap — first to whole blocks, then to two, then
     * four. {@link #worldX}/{@link #worldY}/{@link #worldZ} carry the position Albedo actually
     * renders from and do not have that problem.
     */
    public float x;
    public float y;
    public float z;

    /**
     * Position in full double precision. This is what rendering uses.
     *
     * <p>Kept alongside the float fields rather than replacing them: mods read {@link #x} and
     * changing its type would break every one of them at link time, which is precisely how
     * upstream #11 and #2 came about.
     */
    public double worldX;
    public double worldY;
    public double worldZ;

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
        this(x, y, z, r, g, b, a, 0.0f, radius, 0.0f, (float) Math.PI * 2);
    }

    /** Point light at a position given in full precision. */
    public Light(double x, double y, double z, float r, float g, float b, float a, float radius) {
        this(x, y, z, r, g, b, a, 0.0f, radius, 0.0f, (float) Math.PI * 2);
    }

    public Light(float x, float y, float z, float r, float g, float b, float a, float rx, float ry, float rz, float angle) {
        this((double) x, (double) y, (double) z, r, g, b, a, rx, ry, rz, angle);
    }

    /** Cone light at a position given in full precision. */
    public Light(double x, double y, double z, float r, float g, float b, float a, float rx, float ry, float rz, float angle) {
        this.worldX = x;
        this.worldY = y;
        this.worldZ = z;
        // Mirrored for the public float fields other mods read. Lossy far from the origin, which
        // is exactly why rendering uses the doubles instead.
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
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
        // Doubles, so a position survives the builder intact. Narrowing here would throw away
        // precision before build() ever ran, which is the whole of upstream #8.
        private double x = Double.NaN;
        private double y = Double.NaN;
        private double z = Double.NaN;
        private float r = Float.NaN;
        private float g = Float.NaN;
        private float b = Float.NaN;
        private float a = Float.NaN;
        private float rx = Float.NaN;
        private float ry = Float.NaN;
        private float rz = Float.NaN;
        private float angle = Float.NaN;

        public Builder pos(BlockPos pos) {
            return this.pos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }

        public Builder pos(Vec3d pos) {
            return this.pos(pos.x, pos.y, pos.z);
        }

        public Builder pos(Entity e) {
            return this.pos(e.posX, e.posY, e.posZ);
        }

        public Builder pos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder pos(float x, float y, float z) {
            return this.pos((double) x, (double) y, (double) z);
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
            if (Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z)
                    && Float.isFinite(this.r) && Float.isFinite(this.g) && Float.isFinite(this.b) && Float.isFinite(this.a)
                    && Float.isFinite(this.rx) && Float.isFinite(this.ry) && Float.isFinite(this.rz) && Float.isFinite(this.angle)) {
                return new Light(this.x, this.y, this.z, this.r, this.g, this.b, this.a, this.rx, this.ry, this.rz, this.angle);
            }
            throw new IllegalArgumentException("Position, color, and radius must be set, and cannot be infinite");
        }
    }
}
