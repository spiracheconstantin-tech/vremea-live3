package ro.vremea.wallpaper

import kotlin.math.*

/** Approximate topocentric placement for a decorative live-wallpaper Moon. */
internal object MoonSkyPosition {
    data class Point(val altitudeDeg: Double, val hourAngleDeg: Double)

    fun calculate(timeMillis: Long, latitudeDeg: Double, longitudeDeg: Double): Point {
        val jd = timeMillis / 86400000.0 + 2440587.5
        val d = jd - 2451543.5

        val node = Math.toRadians(125.1228 - 0.0529538083 * d)
        val inclination = Math.toRadians(5.1454)
        val periapsis = Math.toRadians(318.0634 + 0.1643573223 * d)
        val eccentricity = 0.0549
        val meanAnomaly = Math.toRadians(normalizeDegrees(115.3654 + 13.0649929509 * d))

        var eccentricAnomaly = meanAnomaly + eccentricity * sin(meanAnomaly) * (1.0 + eccentricity * cos(meanAnomaly))
        repeat(5) {
            eccentricAnomaly -= (eccentricAnomaly - eccentricity * sin(eccentricAnomaly) - meanAnomaly) /
                (1.0 - eccentricity * cos(eccentricAnomaly))
        }

        val xv = 60.2666 * (cos(eccentricAnomaly) - eccentricity)
        val yv = 60.2666 * sqrt(1.0 - eccentricity * eccentricity) * sin(eccentricAnomaly)
        val trueAnomaly = atan2(yv, xv)
        val radius = hypot(xv, yv)
        val xh = radius * (cos(node) * cos(trueAnomaly + periapsis) - sin(node) * sin(trueAnomaly + periapsis) * cos(inclination))
        val yh = radius * (sin(node) * cos(trueAnomaly + periapsis) + cos(node) * sin(trueAnomaly + periapsis) * cos(inclination))
        val zh = radius * sin(trueAnomaly + periapsis) * sin(inclination)
        val eclipticLongitude = atan2(yh, xh)
        val eclipticLatitude = atan2(zh, hypot(xh, yh))

        val obliquity = Math.toRadians(23.4393 - 3.563E-7 * d)
        val rightAscension = atan2(
            sin(eclipticLongitude) * cos(obliquity) - tan(eclipticLatitude) * sin(obliquity),
            cos(eclipticLongitude)
        )
        val declination = asin(
            sin(eclipticLatitude) * cos(obliquity) +
                cos(eclipticLatitude) * sin(obliquity) * sin(eclipticLongitude)
        )

        val t = (jd - 2451545.0) / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) +
            0.000387933 * t * t - t * t * t / 38710000.0
        val localSidereal = Math.toRadians(normalizeDegrees(gmst + longitudeDeg))
        val hourAngle = normalizeRadians(localSidereal - rightAscension)
        val latitude = Math.toRadians(latitudeDeg.coerceIn(-89.9, 89.9))
        val altitude = asin(
            (sin(latitude) * sin(declination) +
                cos(latitude) * cos(declination) * cos(hourAngle)).coerceIn(-1.0, 1.0)
        )

        return Point(Math.toDegrees(altitude), Math.toDegrees(hourAngle))
    }

    private fun normalizeRadians(value: Double): Double {
        var v = (value + PI) % (2.0 * PI)
        if (v < 0.0) v += 2.0 * PI
        return v - PI
    }

    private fun normalizeDegrees(value: Double): Double {
        var v = value % 360.0
        if (v < 0.0) v += 360.0
        return v
    }
}
