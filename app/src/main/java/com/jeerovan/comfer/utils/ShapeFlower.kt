import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A custom Shape that resembles a flower with a configurable number of petals and rotation.
 *
 * @param petalCount The number of petals the flower should have. Defaults to 4.
 * @param angle The rotation of the flower shape in degrees. Defaults to 0f.
 */
class FlowerShape(private val petalCount: Int = 4, private val angle: Float = 0f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Convert the angle from degrees to radians for trigonometric functions
        val angleInRadians = (angle * PI / 180f).toFloat()

        // Create the path for the flower's petals, applying the rotation
        val petalsPath = createPetalsPath(size, petalCount, angleInRadians)

        return Outline.Generic(petalsPath)
    }
}

/**
 * Creates the outer shape of the flower by combining several circular petal paths.
 *
 * @param size The size of the canvas.
 * @param petalCount The number of petals.
 * @param angleOffset The rotational offset in radians.
 */
private fun createPetalsPath(size: Size, petalCount: Int, angleOffset: Float): Path {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val radius = min(size.width, size.height) / 2f

    val petalRadius = radius / 1.8f
    val petalCenterRadius = radius - petalRadius

    val combinedPath = Path()
    val angleStep = (2 * PI / petalCount).toFloat()

    for (i in 0 until petalCount) {
        // Add the angleOffset to the calculated angle for each petal to apply the rotation
        val angle = angleOffset + i * angleStep
        val petalCenterX = centerX + petalCenterRadius * cos(angle)
        val petalCenterY = centerY + petalCenterRadius * sin(angle)

        val petalPath = Path().apply {
            addOval(
                Rect(
                    left = petalCenterX - petalRadius,
                    top = petalCenterY - petalRadius,
                    right = petalCenterX + petalRadius,
                    bottom = petalCenterY + petalRadius
                )
            )
        }

        combinedPath.op(combinedPath, petalPath, PathOperation.Union)
    }

    return combinedPath
}
