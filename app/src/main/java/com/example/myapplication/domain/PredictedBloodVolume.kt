package com.example.myapplication.domain

import com.example.myapplication.model.Sex
import kotlin.math.roundToInt

/*
 * 성인예측 혈량표 - 수혈의학 2014 [부록 1-A (남성), 1-B (여성)]. Rows are weights (10 lb steps,
 * 100-310 lb), columns heights (2 inch steps, 5'0"-6'2"), values total blood volume in mL.
 *
 * Two values differ from the source as transcribed, both evident typos:
 *  - Weight 104.3kg (230 lb) was given as 103.4kg (228 lb): every other row is a 10 lb step, and
 *    this row's volumes sit exactly one step between 220 and 240 lb.
 *  - 여성 72.5kg x 1.68m was given as 3,695mL - lower than both neighbours in its row and column,
 *    whose trend puts it at 3,965mL (the same digits, swapped).
 */

/** The source's 1.52-1.88m, in cm so input in cm compares exactly. */
private val HEIGHTS_CM = doubleArrayOf(152.0, 158.0, 163.0, 168.0, 173.0, 178.0, 183.0, 188.0)

private val WEIGHTS_KG = doubleArrayOf(
    45.4, 49.9, 54.5, 59.0, 63.5, 68.0, 72.5, 77.0, 81.6, 86.2,
    90.7, 95.3, 99.8, 104.3, 108.9, 113.4, 118.0, 122.5, 127.0,
    131.6, 136.1, 140.6
)

private val MALE_ML = arrayOf(
    intArrayOf(3365, 3500, 3643, 3795, 3957, 4129, 4311, 4503),
    intArrayOf(3512, 3646, 3789, 3941, 4103, 4275, 4457, 4649),
    intArrayOf(3658, 3792, 3935, 4088, 4250, 4422, 4603, 4796),
    intArrayOf(3804, 3938, 4082, 4234, 4396, 4568, 4750, 4942),
    intArrayOf(3951, 4085, 4228, 4380, 4542, 4714, 4896, 5088),
    intArrayOf(4097, 4231, 4374, 4527, 4689, 4860, 5042, 5235),
    intArrayOf(4243, 4377, 4521, 4673, 4835, 5007, 5189, 5381),
    intArrayOf(4389, 4524, 4667, 4819, 4981, 5153, 5335, 5527),
    intArrayOf(4536, 4670, 4813, 4966, 5128, 5299, 5481, 5673),
    intArrayOf(4682, 4816, 4959, 5112, 5274, 5446, 5627, 5820),
    intArrayOf(4828, 4962, 5106, 5258, 5420, 5592, 5774, 5966),
    intArrayOf(4975, 5109, 5252, 5405, 5566, 5738, 5920, 6112),
    intArrayOf(5121, 5255, 5398, 5551, 5713, 5885, 6066, 6258),
    intArrayOf(5267, 5402, 5545, 5697, 5859, 6031, 6213, 6405),
    intArrayOf(5414, 5548, 5692, 5843, 6005, 6177, 6359, 6551),
    intArrayOf(5560, 5694, 5837, 5990, 6152, 6323, 6505, 6698),
    intArrayOf(5706, 5840, 5984, 6136, 6298, 6470, 6652, 6844),
    intArrayOf(5852, 5987, 6130, 6282, 6444, 6616, 6798, 6990),
    intArrayOf(5999, 6133, 6276, 6429, 6591, 6762, 6944, 7136),
    intArrayOf(6145, 6279, 6423, 6575, 6737, 6909, 7091, 7283),
    intArrayOf(6291, 6426, 6569, 6721, 6883, 7055, 7237, 7429),
    intArrayOf(6438, 6572, 6715, 6868, 7030, 7201, 7383, 7575)
)

private val FEMALE_ML = arrayOf(
    intArrayOf(2646, 2776, 2915, 3066, 3220, 3387, 3564, 3750),
    intArrayOf(2796, 2927, 3065, 3214, 3371, 3537, 3714, 3910),
    intArrayOf(2947, 3077, 3216, 3364, 3521, 3688, 3864, 4052),
    intArrayOf(3097, 3227, 3366, 3514, 3671, 3838, 4015, 4201),
    intArrayOf(3247, 3378, 3517, 3665, 3822, 3989, 4165, 4352),
    intArrayOf(3398, 3528, 3667, 3815, 3972, 4139, 4315, 4502),
    intArrayOf(3548, 3678, 3817, 3965, 4123, 4289, 4466, 4652),
    intArrayOf(3698, 3829, 3968, 4116, 4273, 4440, 4616, 4803),
    intArrayOf(3849, 3979, 4118, 4266, 4423, 4590, 4766, 4953),
    intArrayOf(3999, 4129, 4268, 4416, 4574, 4740, 4917, 5103),
    intArrayOf(4150, 4280, 4419, 4567, 4724, 4891, 5067, 5254),
    intArrayOf(4300, 4430, 4569, 4717, 4874, 5041, 5217, 5404),
    intArrayOf(4450, 4581, 4719, 4867, 5025, 5191, 5368, 5554),
    intArrayOf(4601, 4731, 4870, 5018, 5175, 5342, 5518, 5705),
    intArrayOf(4751, 4881, 5020, 5168, 5323, 5492, 5669, 5855),
    intArrayOf(4901, 5032, 5171, 5318, 5476, 5642, 5819, 6005),
    intArrayOf(5052, 5182, 5321, 5469, 5626, 5793, 5969, 6156),
    intArrayOf(5202, 5332, 5471, 5619, 5776, 5943, 6120, 6306),
    intArrayOf(5352, 5483, 5622, 5770, 5927, 6093, 6270, 6457),
    intArrayOf(5500, 5633, 5772, 5920, 6077, 6244, 6420, 6607),
    intArrayOf(5653, 5783, 5922, 6070, 6227, 6394, 6571, 6757),
    intArrayOf(5800, 5934, 6073, 6221, 6378, 6544, 6721, 6908)
)

val BLOOD_VOLUME_TABLE_HEIGHT_CM: ClosedFloatingPointRange<Double> = HEIGHTS_CM.first()..HEIGHTS_CM.last()
val BLOOD_VOLUME_TABLE_WEIGHT_KG: ClosedFloatingPointRange<Double> = WEIGHTS_KG.first()..WEIGHTS_KG.last()

/**
 * @param withinTable False when the height or weight lies outside the table and [volumeMl] was
 * extrapolated from its edge.
 */
data class PredictedBloodVolume(val volumeMl: Int, val withinTable: Boolean)

/**
 * Where [value] falls among [grid]: the index of the segment it's in and how far along it (0..1).
 * Beyond either end, the edge segment is extended, giving a fraction below 0 or above 1.
 */
private fun locate(grid: DoubleArray, value: Double): Pair<Int, Double> {
    val segment = (grid.indexOfLast { it <= value }).coerceIn(0, grid.size - 2)
    return segment to (value - grid[segment]) / (grid[segment + 1] - grid[segment])
}

/**
 * Total blood volume predicted by the 성인예측 혈량표, interpolated between the table's heights and
 * weights. Outside the table it's extrapolated linearly from the nearest edge - exact for weight,
 * along which the table is linear, and an approximation for height - and flagged as such.
 * Null for a non-positive height or weight.
 */
fun predictedBloodVolume(heightCm: Double, weightKg: Double, sex: Sex): PredictedBloodVolume? {
    if (heightCm <= 0 || weightKg <= 0) return null
    val table = when (sex) {
        Sex.MALE -> MALE_ML
        Sex.FEMALE -> FEMALE_ML
    }
    val (row, rowT) = locate(WEIGHTS_KG, weightKg)
    val (col, colT) = locate(HEIGHTS_CM, heightCm)
    val lighter = table[row][col] + (table[row][col + 1] - table[row][col]) * colT
    val heavier = table[row + 1][col] + (table[row + 1][col + 1] - table[row + 1][col]) * colT
    val volume = lighter + (heavier - lighter) * rowT
    return PredictedBloodVolume(
        volumeMl = volume.roundToInt(),
        withinTable = heightCm in BLOOD_VOLUME_TABLE_HEIGHT_CM && weightKg in BLOOD_VOLUME_TABLE_WEIGHT_KG
    )
}
