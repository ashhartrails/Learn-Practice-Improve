package com.example.learnanything.core.model.resources

/**
 * Allow-listed PhET simulations (HTML5). The AI may only return one of these keys; the app
 * builds the URL. Every slug was checked to exist on 2026-11-17.
 * PhET Interactive Simulations, University of Colorado Boulder, CC BY 4.0.
 */
object PhetCatalog {
    data class Sim(val slug: String, val title: String, val area: String) {
        val key: String get() = "phet:$slug"
        val url: String get() = "https://phet.colorado.edu/sims/html/$slug/latest/${slug}_all.html"
    }

    const val ATTRIBUTION = "PhET Interactive Simulations, University of Colorado Boulder, licensed under CC BY 4.0."
    const val HOST = "phet.colorado.edu"

    val all: List<Sim> = listOf(
        Sim("states-of-matter", "States of Matter", "chemistry"),
        Sim("states-of-matter-basics", "States of Matter: Basics", "chemistry"),
        Sim("gas-properties", "Gas Properties", "chemistry"),
        Sim("diffusion", "Diffusion", "chemistry"),
        Sim("build-an-atom", "Build an Atom", "chemistry"),
        Sim("isotopes-and-atomic-mass", "Isotopes and Atomic Mass", "chemistry"),
        Sim("build-a-molecule", "Build a Molecule", "chemistry"),
        Sim("molecule-shapes", "Molecule Shapes", "chemistry"),
        Sim("molecule-polarity", "Molecule Polarity", "chemistry"),
        Sim("balancing-chemical-equations", "Balancing Chemical Equations", "chemistry"),
        Sim("reactants-products-and-leftovers", "Reactants, Products and Leftovers", "chemistry"),
        Sim("ph-scale", "pH Scale", "chemistry"),
        Sim("acid-base-solutions", "Acid-Base Solutions", "chemistry"),
        Sim("concentration", "Concentration", "chemistry"),
        Sim("molarity", "Molarity", "chemistry"),
        Sim("beers-law-lab", "Beer's Law Lab", "chemistry"),
        Sim("models-of-the-hydrogen-atom", "Models of the Hydrogen Atom", "physics"),
        Sim("blackbody-spectrum", "Blackbody Spectrum", "physics"),
        Sim("forces-and-motion-basics", "Forces and Motion: Basics", "physics"),
        Sim("friction", "Friction", "physics"),
        Sim("projectile-motion", "Projectile Motion", "physics"),
        Sim("vector-addition", "Vector Addition", "physics"),
        Sim("collision-lab", "Collision Lab", "physics"),
        Sim("energy-skate-park", "Energy Skate Park", "physics"),
        Sim("energy-skate-park-basics", "Energy Skate Park: Basics", "physics"),
        Sim("energy-forms-and-changes", "Energy Forms and Changes", "physics"),
        Sim("gravity-and-orbits", "Gravity and Orbits", "physics"),
        Sim("gravity-force-lab", "Gravity Force Lab", "physics"),
        Sim("my-solar-system", "My Solar System", "physics"),
        Sim("keplers-laws", "Kepler's Laws", "physics"),
        Sim("pendulum-lab", "Pendulum Lab", "physics"),
        Sim("masses-and-springs", "Masses and Springs", "physics"),
        Sim("hookes-law", "Hooke's Law", "physics"),
        Sim("density", "Density", "physics"),
        Sim("buoyancy", "Buoyancy", "physics"),
        Sim("under-pressure", "Under Pressure", "physics"),
        Sim("wave-on-a-string", "Wave on a String", "physics"),
        Sim("waves-intro", "Waves Intro", "physics"),
        Sim("wave-interference", "Wave Interference", "physics"),
        Sim("sound-waves", "Sound Waves", "physics"),
        Sim("bending-light", "Bending Light", "physics"),
        Sim("geometric-optics", "Geometric Optics", "physics"),
        Sim("color-vision", "Color Vision", "physics"),
        Sim("greenhouse-effect", "Greenhouse Effect", "physics"),
        Sim("balloons-and-static-electricity", "Balloons and Static Electricity", "physics"),
        Sim("john-travoltage", "John Travoltage", "physics"),
        Sim("coulombs-law", "Coulomb's Law", "physics"),
        Sim("charges-and-fields", "Charges and Fields", "physics"),
        Sim("ohms-law", "Ohm's Law", "physics"),
        Sim("resistance-in-a-wire", "Resistance in a Wire", "physics"),
        Sim("circuit-construction-kit-dc", "Circuit Construction Kit: DC", "physics"),
        Sim("circuit-construction-kit-ac", "Circuit Construction Kit: AC", "physics"),
        Sim("capacitor-lab-basics", "Capacitor Lab: Basics", "physics"),
        Sim("faradays-law", "Faraday's Law", "physics"),
        Sim("natural-selection", "Natural Selection", "biology"),
        Sim("gene-expression-essentials", "Gene Expression Essentials", "biology"),
        Sim("neuron", "Neuron", "biology"),
        Sim("arithmetic", "Arithmetic", "maths"),
        Sim("fractions-intro", "Fractions Intro", "maths"),
        Sim("fractions-equality", "Fractions: Equality", "maths"),
        Sim("area-model-multiplication", "Area Model Multiplication", "maths"),
        Sim("number-line-integers", "Number Line: Integers", "maths"),
        Sim("unit-rates", "Unit Rates", "maths"),
        Sim("mean-share-and-balance", "Mean: Share and Balance", "maths"),
        Sim("expression-exchange", "Expression Exchange", "maths"),
        Sim("equality-explorer", "Equality Explorer", "maths"),
        Sim("function-builder", "Function Builder", "maths"),
        Sim("graphing-lines", "Graphing Lines", "maths"),
        Sim("graphing-quadratics", "Graphing Quadratics", "maths"),
        Sim("trig-tour", "Trig Tour", "maths"),
        Sim("calculus-grapher", "Calculus Grapher", "maths"),
        Sim("curve-fitting", "Curve Fitting", "statistics"),
        Sim("least-squares-regression", "Least-Squares Regression", "statistics"),
        Sim("center-and-variability", "Center and Variability", "statistics"),
        Sim("plinko-probability", "Plinko Probability", "statistics"),
    )

    private val byKey = all.associateBy { it.key }

    fun find(key: String): Sim? = byKey[key.trim()]

    /** One line per sim, for the prompt. */
    fun promptList(): String = all.joinToString("\n") { "${it.key}: ${it.title} (${it.area})" }
}
