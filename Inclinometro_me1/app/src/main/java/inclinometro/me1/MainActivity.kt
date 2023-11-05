package inclinometro.me1

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.round
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(),SensorEventListener {

    private lateinit var textAngX: TextView
    private lateinit var textAngY: TextView
    private lateinit var textAngZ: TextView
    private lateinit var textGravity: TextView

    private lateinit var mSensorManager : SensorManager
    private var mAccelerometer : Sensor?= null

    private var x = 0.0F
    private var y = 0.0F
    private var z = 0.0F

    private var mLastX = 0.0F
    private var mLastY = 0.0F
    private var mLastZ = 0.0F
    private var mInitialized = false

    // variables del Exponential Moving Average
    private val alpha_decay = 0.7
    private var ema_x = 0.0
    private var ema_y = 0.0
    private var ema_z = 0.0
    private var ema_count_x = 0.0
    private var ema_count_y = 0.0
    private var ema_count_z = 0.0

    // Calibracion
    private var CALIBRATION_FLAG = false
    private var NOISE_X = 0.02
    private var NOISE_Y = 0.02
    private var NOISE_Z = 0.02
    private var calib_count = 0
    private val CALIB_SAMPLES = 100
    private val CALIB_CONSTANT = 1.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textAngX = findViewById<TextView>(R.id.textAngX)
        textAngY = findViewById<TextView>(R.id.textAngY)
        textAngZ = findViewById<TextView>(R.id.textAngZ)
        textGravity = findViewById<TextView>(R.id.textG)

        // Se inicializa el sensor Giroscopio
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        //validación y registro del sensor
        if (mAccelerometer != null) {
            // Gyroscope sensor found!, Register the sensor listener
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            val toast = Toast.makeText(this, "No Gyroscope sensor (wtf?!). Terminating...", Toast.LENGTH_LONG)
            toast.show()
            finish()
            return
        }

        val buttonCal = findViewById<Button>(R.id.buttonCalibrar)
        //buttonCal.text = "Calibrar"
        buttonCal.setOnClickListener {
            CALIBRATION_FLAG = true
        }
    }

    //Interrumpe cuando hay un nuevo valor
    override fun onSensorChanged( event: SensorEvent?){
        if (event != null) {

            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                Log.i("SensorChanged", "Transition detected.")

                x = event.values[0]
                y = event.values[1]
                z = event.values[2]
            }

            if (!mInitialized) {
                mLastX = x
                ema_x = 0.0
                mLastY = y
                ema_y = 0.0
                mLastZ = z
                ema_z = 0.0

                ema_count_x = 0.0
                ema_count_y = 0.0
                ema_count_z = 0.0

                mInitialized = true
            }

            //filtro el ruido
            if (mInitialized && !CALIBRATION_FLAG) {
                // Actualizo el moving average exponencial,
                // siempre que detectemos un cambio mas alla del piso de ruido (empirico)

                if (abs(mLastX - x) > NOISE_X) {
                    ema_x = x + (1 - alpha_decay) * ema_x
                    ema_count_x = 1 + (1 - alpha_decay) * ema_count_x
                }
                mLastX = x
                if (abs(mLastY - y) > NOISE_Y) {
                    ema_y = y + (1 - alpha_decay) * ema_y
                    ema_count_y = 1 + (1 - alpha_decay) * ema_count_y
                }
                mLastY = y
                if (abs(mLastZ - z) > NOISE_Z) {
                    ema_z = z + (1 - alpha_decay) * ema_z
                    ema_count_z = 1 + (1 - alpha_decay) * ema_count_z
                }
                mLastZ = z

                //---
            }

            // Modo calibracion
            if (CALIBRATION_FLAG) {
                if (calib_count == 0) {
                    // Reinicio los promedios
                    ema_x = abs(mLastX - x).toDouble()
                    ema_y = abs(mLastY - y).toDouble()
                    ema_z = abs(mLastZ - z).toDouble()
                    mLastX = x
                    mLastY = y
                    mLastZ = z
                } else {
                    // Ahora hago un promedio normal, ya que el
                    // dispositivo se encuentra siempre en el mismo estado
                    // durante la calibracion (no hay que moverlo)
                    ema_x = (calib_count * ema_x + abs(mLastX - x)) / (calib_count + 1)
                    ema_y = (calib_count * ema_x + abs(mLastY - y)) / (calib_count + 1)
                    ema_z = (calib_count * ema_x + abs(mLastZ - z)) / (calib_count + 1)
                    mLastX = x
                    mLastY = y
                    mLastZ = z
                    // Esto se llama "running moving average (RMA)"
                    // Tiene esa forma ya que nuestro N (calib_count) empieza
                    // en 0 en lugar de 1, como la expresion matematica.
                }

                if (calib_count > CALIB_SAMPLES) {
                    // asigno los nuevos valores de ruido
                    NOISE_X = ema_x * CALIB_CONSTANT
                    NOISE_Y = ema_y * CALIB_CONSTANT
                    NOISE_Z = ema_z * CALIB_CONSTANT

                    calib_count = 0         // Reseteo el contador
                    CALIBRATION_FLAG = false// Salgo del estado calibracion
                    mInitialized = false    // Me pongo en modo no inicializado (reinicio EMA)

                    val context = applicationContext
                    val text: CharSequence = "Ruido: X = " + String.format(
                        "%.4f",
                        NOISE_X
                    ) + "Ruido: Y = " + String.format(
                        "%.4f",
                        NOISE_Y
                    ) + "Ruido: Z = " + String.format("%.4f", NOISE_Z)
                    val duration = Toast.LENGTH_LONG
                    val toast = Toast.makeText(context, text, duration)
                    toast.show()
                }
                calib_count++
            }

            /******************* código para actualizar la rotación *****************/
            // Asigno el nuevo valor
            val use_x = ema_x / ema_count_x
            val use_y = ema_y / ema_count_y
            val use_z = ema_z / ema_count_z

            // Calculo el angulo
            val g = 9.807 // m/seg^2
            var ang_med_x = asin(use_x / g) * 180.0 / PI
            var ang_med_y = asin(use_y / g) * 180.0 / PI
            var ang_med_z = asin(use_z / g) * 180.0 / PI

            //muestro los angulos en pantalla: αx = 45 °
            val textX = "\u03B1x = " + String.format("%.2f",ang_med_x) + " °"
            val textY = "\u03B1y = " + String.format("%.2f",ang_med_y) + " °"
            val textZ = "\u03B1z = " + String.format("%.2f",ang_med_z) + " °"
            val textG = "|g| = " + String.format("%.2f",
                sqrt( use_x*use_x + use_y*use_y + use_z*use_z ) )
            textAngX.text = textX
            textAngY.text = textY
            textAngZ.text = textZ
            textGravity.text = textG

            val imagen = findViewById<ImageView>(R.id.imageBurbuja)
            //normalizamos el valor de g y extendemos a 250 pixel. <- radio del circulo
            imagen.translationX = -(use_x*250/9.8).toFloat()
            imagen.translationY = (use_y*250/9.8).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }
}