package com.seba.malosh.fragments.desafios

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.seba.malosh.R
import com.seba.malosh.activities.BienvenidaActivity
import java.util.concurrent.TimeUnit
import java.util.*

class DesafiosDiariosFragment : Fragment() {

    private lateinit var contenedorDesafios: LinearLayout
    private lateinit var progresoChecklist: LinearLayout
    private lateinit var volverButton: Button
    private lateinit var aceptarDesafioButton: Button
    private lateinit var cancelarDesafioButton: Button
    private lateinit var verProgresoButton: Button
    private lateinit var desafioDescripcion: TextView
    private lateinit var tiempoRestanteTextView: TextView

    private val desafiosList = mutableListOf<String>()
    private var currentDesafio: String? = null
    private var desafioEnProgreso = false
    private val handler = Handler()
    private lateinit var registeredHabits: ArrayList<String>
    private var countDownTimer: CountDownTimer? = null
    private val checkBoxes = mutableListOf<CheckBox>()
    private lateinit var sharedPreferences: SharedPreferences
    private var tiempoRestante: Long = 0
    private val DURACION_DESAFIO: Long = 60000L
    private var sessionID: String = UUID.randomUUID().toString()
    private var tiempoInicioDesafio: Long = 0L

    companion object {
        private const val HABITOS_KEY = "habitos_registrados"
        private const val TEMPORIZADOR_INICIO_KEY = "temporizador_inicio"
        private const val TEMPORIZADOR_DURACION = 20000L

        fun newInstance(habits: ArrayList<String>): DesafiosDiariosFragment {
            val fragment = DesafiosDiariosFragment()
            val bundle = Bundle()
            bundle.putStringArrayList(HABITOS_KEY, habits)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_desafios_diarios, container, false)

        contenedorDesafios = view.findViewById(R.id.contenedorDesafios)
        progresoChecklist = view.findViewById(R.id.progresoChecklist)
        volverButton = view.findViewById(R.id.volverButton)
        aceptarDesafioButton = view.findViewById(R.id.aceptarDesafioButton)
        cancelarDesafioButton = view.findViewById(R.id.cancelarDesafioButton)
        verProgresoButton = view.findViewById(R.id.verProgresoButton)
        desafioDescripcion = view.findViewById(R.id.desafioDescripcion)
        tiempoRestanteTextView = view.findViewById(R.id.tiempoRestanteTextView)

        registeredHabits = arguments?.getStringArrayList(HABITOS_KEY) ?: arrayListOf()

        sharedPreferences = requireContext().getSharedPreferences("temporizador_prefs", Context.MODE_PRIVATE)
        val inicioTemporizador = sharedPreferences.getLong(TEMPORIZADOR_INICIO_KEY, 0L)

        if (inicioTemporizador > 0L) {
            reanudarTemporizador(inicioTemporizador)
        } else {
            generarDesafiosSiEsNecesario()
        }

        aceptarDesafioButton.setOnClickListener {
            aceptarDesafio()
        }

        cancelarDesafioButton.setOnClickListener {
            cancelarDesafio()
        }

        volverButton.setOnClickListener {
            (activity as? BienvenidaActivity)?.mostrarElementosUI()
            requireActivity().supportFragmentManager.popBackStack()
        }

        return view
    }

    private fun generarDesafiosSiEsNecesario() {
        val desafioGuardado = obtenerDesafioEnProgreso(requireContext())

        if (desafioGuardado != null) {
            currentDesafio = desafioGuardado
            mostrarDesafioEnProgreso()
        } else {
            generarDesafios(registeredHabits)
            mostrarDesafio()
        }
    }


    private fun iniciarTemporizador20Segundos() {
        val tiempoInicio = System.currentTimeMillis()
        sharedPreferences.edit().putLong(TEMPORIZADOR_INICIO_KEY, tiempoInicio).apply()
        reanudarTemporizador(tiempoInicio)
    }

    private fun reanudarTemporizador(tiempoInicio: Long) {
        val tiempoActual = System.currentTimeMillis()
        val tiempoRestante = TEMPORIZADOR_DURACION - (tiempoActual - tiempoInicio)

        if (tiempoRestante > 0) {
            actualizarTemporizador(tiempoRestante)
        } else {
            desafioDescripcion.text = "¡Nuevo desafío disponible!"
            generarDesafios(registeredHabits)
            mostrarDesafio()
            aceptarDesafioButton.visibility = View.VISIBLE
            aceptarDesafioButton.isEnabled = true
            sharedPreferences.edit().remove(TEMPORIZADOR_INICIO_KEY).apply()
        }
    }

    private fun actualizarTemporizador(tiempoRestante: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(tiempoRestante, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tiempoRestanteTextView.visibility = View.VISIBLE // Mostrar el temporizador
                tiempoRestanteTextView.text = String.format(
                    "Próximo desafío disponible en %d segundos.",
                    TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)
                )
            }

            override fun onFinish() {
                tiempoRestanteTextView.text = "¡Nuevo desafío disponible!"
                generarDesafios(registeredHabits)
                mostrarDesafio()
                aceptarDesafioButton.visibility = View.VISIBLE
                aceptarDesafioButton.isEnabled = true
            }
        }.start()
    }

    private fun mostrarDesafioEnProgreso() {
        aceptarDesafioButton.isEnabled = false
        cancelarDesafioButton.visibility = View.VISIBLE
        progresoChecklist.visibility = View.VISIBLE // Asegúrate de mostrar el contenedor de los checkboxes
        agregarChecklist() // Añade los checkboxes

        // Si el temporizador ya estaba corriendo, no lo inicies de nuevo
        val tiempoInicio = sharedPreferences.getLong(TEMPORIZADOR_INICIO_KEY, 0L)
        if (tiempoInicio > 0L) {
            reanudarTemporizador(tiempoInicio)
        } else {
            iniciarTemporizador(DURACION_DESAFIO, false)
        }
    }


    private fun agregarChecklist() {
        progresoChecklist.removeAllViews() // Limpiar el contenedor
        checkBoxes.clear()

        val nombresCheckBoxes = listOf("Inicio", "En progreso", "Casi por terminar", "Terminado")

        for (i in nombresCheckBoxes.indices) {
            val checkBoxExtra = CheckBox(context).apply {
                text = nombresCheckBoxes[i]
                textSize = 16f
                setTextColor(resources.getColor(android.R.color.black))

                // Centramos el checkbox y el texto
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL // Alinea horizontalmente al centro
                    setMargins(16, 8, 16, 8) // Ajusta el margen si lo deseas
                }
                isChecked = i == 0
                isEnabled = i == 0
            }
            checkBoxes.add(checkBoxExtra)
            progresoChecklist.addView(checkBoxExtra)
        }

        progresoChecklist.gravity = android.view.Gravity.CENTER // Centra todo el contenedor
    }



    private fun iniciarTemporizador(duracion: Long, esNuevoDesafio: Boolean) {
        if (esNuevoDesafio) {
            tiempoInicioDesafio = System.currentTimeMillis() // Almacenar el tiempo de inicio
            sharedPreferences.edit().putLong("$sessionID-tiempoInicio", tiempoInicioDesafio).apply()
        } else {
            tiempoInicioDesafio = sharedPreferences.getLong("$sessionID-tiempoInicio", 0L)
        }

        val tiempoActual = System.currentTimeMillis()
        val tiempoTranscurrido = tiempoActual - tiempoInicioDesafio
        tiempoRestante = duracion - tiempoTranscurrido

        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(tiempoRestante, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tiempoRestante = millisUntilFinished
                val minutos = (millisUntilFinished / 60000)
                val segundos = (millisUntilFinished % 60000) / 1000

                tiempoRestanteTextView.text = String.format("%02d:%02d", minutos, segundos)
                desbloquearCheckBoxes((duracion - millisUntilFinished).toFloat() / duracion.toFloat() * 100)
            }

            override fun onFinish() {
                desbloquearTodosCheckBoxes() // Desbloquear todos los checkboxes cuando termine el temporizador
                tiempoRestanteTextView.text = "¡Desafío completado!"
            }
        }.start()
    }

    private fun desbloquearCheckBoxes(porcentaje: Float) {
        // Umbrales para desbloquear cada checkbox según el porcentaje del tiempo transcurrido
        val umbrales = listOf(0f, 33.3f, 66.6f, 90f)

        for (i in checkBoxes.indices) {
            if (porcentaje >= umbrales.getOrNull(i) ?: 100f && !checkBoxes[i].isEnabled) {
                checkBoxes[i].isEnabled = true
            }
        }
    }

    private fun desbloquearTodosCheckBoxes() {
        checkBoxes.forEach { it.isEnabled = true }
    }

    private fun cancelarDesafio() {
        countDownTimer?.cancel()
        desafioEnProgreso = false
        currentDesafio = null
        iniciarTemporizador20Segundos()
    }

    private fun aceptarDesafio() {
        if (!desafioEnProgreso) {
            desafioEnProgreso = true
            guardarDesafioEnProgreso(requireContext(), currentDesafio, true)
            Toast.makeText(context, "¡Desafío aceptado!", Toast.LENGTH_SHORT).show()
            mostrarDesafioEnProgreso()
        }
    }

    private fun generarDesafios(habitos: List<String>) {
        desafiosList.clear()

        for (habito in habitos) {
            when (habito.lowercase().trim()) {
                "cafeína", "consumo de cafeína" -> desafiosList.addAll(
                    listOf(
                        "No tomes café en las próximas 2 horas.",
                        "Reemplaza el café de la tarde con agua.",
                        "No consumas cafeína después del mediodía.",
                        "Evita el café mientras trabajas hoy.",
                        "Reduce tu ingesta de café a una taza al día.",
                        "Reemplaza el café con té durante la mañana.",
                        "No tomes bebidas energéticas hoy."
                    )
                )

                "dormir mal", "dormir a deshoras" -> desafiosList.addAll(
                    listOf(
                        "No duermas durante el día.",
                        "Duerme al menos 7 horas esta noche.",
                        "Apaga tus dispositivos electrónicos 30 minutos antes de dormir.",
                        "Evita tomar café después de las 6 p.m.",
                        "Realiza una rutina de relajación antes de dormir.",
                        "Acuéstate antes de las 11 p.m.",
                        "Despiértate a la misma hora mañana."
                    )
                )

                "interrumpir a otros" -> desafiosList.addAll(
                    listOf(
                        "No interrumpas a nadie en una conversación durante las próximas 3 horas.",
                        "Escucha activamente durante una conversación sin interrumpir.",
                        "Deja que los demás terminen de hablar antes de dar tu opinión.",
                        "Practica la paciencia en una reunión evitando interrumpir.",
                        "Asegúrate de dar espacio para que los demás hablen primero."
                    )
                )

                "mala alimentación" -> desafiosList.addAll(
                    listOf(
                        "Evita la comida rápida durante todo el día.",
                        "Come 3 comidas balanceadas hoy.",
                        "Reemplaza los snacks poco saludables por frutas.",
                        "Reduce el consumo de azúcares en tu próxima comida.",
                        "Incluye verduras en tu almuerzo.",
                        "Come una comida casera hoy."
                    )
                )

                "comer a deshoras" -> desafiosList.addAll(
                    listOf(
                        "No comas después de las 10 p.m.",
                        "Establece horarios regulares para tus comidas.",
                        "No comas nada entre comidas durante las próximas 3 horas.",
                        "Desayuna dentro de la primera hora de despertar.",
                        "Evita comer snacks después de la cena.",
                        "Come tus tres comidas a la misma hora todos los días.",
                        "No comas nada durante las próximas 2 horas."
                    )
                )

                "poco ejercicio" -> desafiosList.addAll(
                    listOf(
                        "Realiza una caminata de 30 minutos hoy.",
                        "Haz 15 minutos de estiramientos en casa.",
                        "Realiza 10 flexiones en tu descanso.",
                        "Sube las escaleras en lugar de usar el ascensor.",
                        "Realiza una rutina rápida de ejercicios al levantarte.",
                        "Haz al menos 20 sentadillas hoy.",
                        "Camina en lugar de conducir si es posible hoy."
                    )
                )

                "alcohol" -> desafiosList.addAll(
                    listOf(
                        "No beber alcohol por 2 horas.",
                        "No consumir alcohol durante todo el día.",
                        "Evita tomar más de un vaso de alcohol durante 4 horas.",
                        "No consumas bebidas alcohólicas hasta la noche.",
                        "No tomes alcohol mientras estás en una reunión social.",
                        "Reemplaza el alcohol con agua en tu siguiente comida.",
                        "No consumas bebidas alcohólicas hoy."
                    )
                )

                "fumar" -> desafiosList.addAll(
                    listOf(
                        "No fumes durante las próximas 3 horas.",
                        "Evita fumar un cigarrillo después del almuerzo.",
                        "Intenta reducir tu consumo de cigarrillos a la mitad hoy.",
                        "No fumes en las próximas 5 horas.",
                        "Fuma solo la mitad de tu cigarrillo en tu siguiente descanso.",
                        "Evita fumar mientras trabajas.",
                        "No fumes en espacios cerrados durante todo el día."
                    )
                )

                "mala higiene" -> desafiosList.addAll(
                    listOf(
                        "Cepilla tus dientes después de cada comida hoy.",
                        "Lávate las manos antes y después de cada comida.",
                        "Dedica 10 minutos a limpiar tu espacio personal.",
                        "Toma una ducha antes de acostarte.",
                        "Lávate la cara cada mañana.",
                        "Lávate las manos cada vez que salgas del baño.",
                        "Realiza una limpieza rápida de tu habitación."
                    )
                )

                else -> Toast.makeText(
                    context,
                    "No se encontraron desafíos para el hábito: $habito",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        desafiosList.shuffle()
    }

    private fun mostrarDesafio() {
        currentDesafio = desafiosList.firstOrNull()
        contenedorDesafios.removeAllViews()
        currentDesafio?.let {
            val textView = TextView(context).apply {
                text = it
                textSize = 18f
                setTextColor(resources.getColor(android.R.color.white))
            }
            contenedorDesafios.addView(textView)
        }
    }

    private fun guardarDesafioEnProgreso(context: Context, desafio: String?, enProgreso: Boolean) {
        val sharedPreferences = context.getSharedPreferences("desafio_prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        if (enProgreso) {
            editor.putString("desafio_actual", desafio)
            editor.putBoolean("en_progreso", true)
        } else {
            editor.remove("desafio_actual")
            editor.putBoolean("en_progreso", false)
        }
        editor.apply()
    }

    private fun obtenerDesafioEnProgreso(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("desafio_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("desafio_actual", null)
    }
}
