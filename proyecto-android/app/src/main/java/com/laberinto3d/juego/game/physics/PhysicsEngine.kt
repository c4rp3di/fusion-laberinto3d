package com.laberinto3d.juego.game.physics

import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.core.Tuning
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Motor de física de paso fijo. Mundo estático de cajas AABB, una bola esférica y
 * una gravedad vectorial que cambia según la inclinación del móvil.
 * Sólo se llama desde el hilo GL.
 */
class PhysicsEngine(
    private val world: World,
    @JvmField val ball: Ball,
    private val start: Vec3
) {
    /** Gravedad actual (la fija GameLoop cada paso a partir del acelerómetro). */
    @JvmField val gravity = Vec3(0f, -Config.GRAVITY, 0f)

    /** Punto de reaparición (inicio o último checkpoint). Centro de la bola. */
    private val spawn = Vec3(start.x, start.y, start.z)

    private val rotating = world.rotating
    /** Esfera giratoria: velocidad y aceleración angulares de la esfera en el sistema del cuerpo (las fija GameLoop). */
    @JvmField val omega = Vec3()
    @JvmField val alpha = Vec3()
    /** Esfera giratoria: sentido de la pista y "arriba" de la pista en el punto de reaparición actual (cuerpo). */
    @JvmField val spawnT = Vec3()
    @JvmField val spawnU = Vec3()
    private var gUx = 0f; private var gUy = 1f; private var gUz = 0f

    /** Índice del último checkpoint alcanzado (-1 = ninguno). */
    var checkpointIndex = -1
        private set

    /** Aviso al alcanzar un checkpoint nuevo (índice). */
    var checkpointListener: ((Int) -> Unit)? = null

    var won = false
        private set
    var fell = false
        private set

    /** Notificación de impacto contra pared: (velocidad de impacto, normal x, normal z). */
    var impactListener: ((Float, Float, Float) -> Unit)? = null
    /** Notificación de eventos raros (atascada, velocidad excesiva...) para el log. */
    var warnListener: ((String) -> Unit)? = null

    // ---- Estadísticas para el overlay de depuración ----
    @JvmField var contactsLastStep = 0
    @JvmField var totalContacts = 0L
    @JvmField var maxImpactSeen = 0f
    @JvmField var resetsByStuck = 0
    @JvmField var stepsRun = 0L

    private val contact = Contact()
    private val gridBuf = IntArray(512)
    private var gridCount = -1
    private var simTime = 0f
    /** Superficie del suelo sobre el que rodó la bola en el paso anterior (0 normal, 1 hielo, 2 arena). */
    private var surfaceNow = 0
    private var contactCount = 0
    private var stuckCounter = 0
    private var warnedSpeed = false

    init {
        if (rotating) setSpawnFrame(0, start.x, start.y, start.z)
        resetBall()
    }

    /** Esfera giratoria: calcula el sentido de la pista y el "arriba" (hacia el centro) en un punto de reaparición. */
    private fun setSpawnFrame(frameIdx: Int, x: Float, y: Float, z: Float) {
        val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        spawnU.set(-x / l, -y / l, -z / l)
        val f = world.frameT
        if (f.size >= frameIdx * 3 + 3) spawnT.set(f[frameIdx * 3], f[frameIdx * 3 + 1], f[frameIdx * 3 + 2])
    }

    /** Punto de reaparición para el suelo [c]: sobre la pista, hacia el centro de la esfera. */
    private fun setSpawnFromFloor(frameIdx: Int, c: Vec3) {
        val l = sqrt(c.x * c.x + c.y * c.y + c.z * c.z).coerceAtLeast(1e-6f)
        val ux = -c.x / l; val uy = -c.y / l; val uz = -c.z / l
        spawn.set(c.x + ux * (ball.radius + 0.01f), c.y + uy * (ball.radius + 0.01f), c.z + uz * (ball.radius + 0.01f))
        setSpawnFrame(frameIdx, c.x, c.y, c.z)
    }

    /** Reaparece en el último checkpoint (o en el inicio). */
    fun resetBall() {
        ball.placeAt(spawn)
        won = false
        fell = false
        stuckCounter = 0
        respawnListener?.invoke()
    }

    /** Aviso tras cada reaparición (la esfera giratoria se realinea con la pista). */
    var respawnListener: (() -> Unit)? = null

    /** Restaura un checkpoint guardado (partida reanudada): la bola reaparece allí. */
    fun restoreCheckpoint(index: Int): Boolean {
        val cps = world.checkpoints
        if (index < 0 || index >= cps.size) return false
        val c = cps[index]
        checkpointIndex = index
        if (rotating) setSpawnFromFloor(index + 1, c) else spawn.set(c.x, c.y + ball.radius, c.z)
        resetBall()
        return true
    }

    fun step(dt: Float) {
        val b = ball
        val v = b.velocity
        val p = b.position

        b.restitution = Tuning.restitution
        b.friction = Tuning.wallFriction
        b.linearDamping = Tuning.damping

        b.prevPosition.set(p)
        b.prevRotation.set(b.rotation)

        // 0) plataformas móviles
        simTime += dt
        world.updateMovers(simTime, dt)

        // 1) gravedad
        v.x += gravity.x * dt
        v.y += gravity.y * dt
        v.z += gravity.z * dt
        if (rotating) {
            // fuerzas ficticias del sistema de referencia que gira: -2 w x v - w x (w x r) - a x r
            val wx = omega.x; val wy = omega.y; val wz = omega.z
            val cox = wy * v.z - wz * v.y; val coy = wz * v.x - wx * v.z; val coz = wx * v.y - wy * v.x
            val rx = wy * p.z - wz * p.y; val ry = wz * p.x - wx * p.z; val rz = wx * p.y - wy * p.x
            val cex = wy * rz - wz * ry; val cey = wz * rx - wx * rz; val cez = wx * ry - wy * rx
            val eux = alpha.y * p.z - alpha.z * p.y; val euy = alpha.z * p.x - alpha.x * p.z; val euz = alpha.x * p.y - alpha.y * p.x
            v.x -= (2f * cox + cex + eux) * dt
            v.y -= (2f * coy + cey + euy) * dt
            v.z -= (2f * coz + cez + euz) * dt
            // "arriba" local = opuesto a la gravedad en el cuerpo
            val gl = sqrt(gravity.x * gravity.x + gravity.y * gravity.y + gravity.z * gravity.z)
            if (gl > 1e-4f) { gUx = -gravity.x / gl; gUy = -gravity.y / gl; gUz = -gravity.z / gl }
        }

        // 2) amortiguamiento lineal (hielo: casi nada; arena: mucho)
        val dampK = when (surfaceNow) { 1 -> 0.08f; 2 -> 5f; else -> 1f }
        val damp = (1f - b.linearDamping * dampK * dt).coerceAtLeast(0f)
        v.x *= damp; v.y *= damp; v.z *= damp

        // Límite de velocidad (red de seguridad contra el tunneling)
        val sp = v.length()
        if (sp > Config.SPEED_WARN && !warnedSpeed) {
            warnedSpeed = true
            warnListener?.invoke("Velocidad alta: ${"%.1f".format(sp)} m/s")
        }
        if (sp > Config.SPEED_MAX) {
            val k = Config.SPEED_MAX / sp
            v.x *= k; v.y *= k; v.z *= k
        }

        // 3) integración
        p.x += v.x * dt
        p.y += v.y * dt
        p.z += v.z * dt

        // 4) colisiones (cajas, plataformas móviles y triángulos de rampas)
        gridCount = -1
        val grid = world.triGrid
        if (grid != null) {
            val m = b.radius + 0.25f
            gridCount = grid.query(p.x - m, p.y - m, p.z - m, p.x + m, p.y + m, p.z + m, gridBuf)
        }
        contactCount = 0
        surfaceNow = 0
        for (iter in 0 until Config.COLLISION_ITERATIONS) {
            for (i in world.boxes.indices) {
                val box = world.boxes[i]
                if (!Collision.sphereAabb(p.x, p.y, p.z, b.radius, box, contact)) continue
                val dyn = box.dynamic
                resolve(iter, dt, if (dyn) box.vel.x else 0f, if (dyn) box.vel.y else 0f, if (dyn) box.vel.z else 0f, dyn, box.dvx, box.dvz)
                if (contact.ny > 0.7f && box.surface != 0) surfaceNow = box.surface
            }
            val tv = world.tris
            val tn = world.triNormals
            if (gridCount >= 0) {
                for (k in 0 until gridCount) {
                    val t = gridBuf[k]
                    if (Collision.sphereTri(p.x, p.y, p.z, b.radius, tv, t * 9, tn[t * 3], tn[t * 3 + 1], tn[t * 3 + 2], contact)) {
                        resolve(iter, dt, 0f, 0f, 0f, false, 0f, 0f)
                        if (contact.ny > 0.7f && t < world.triSurf.size && world.triSurf[t] != 0) surfaceNow = world.triSurf[t]
                    }
                }
            } else {
                var ti = 0
                while (ti < tn.size) {
                    if (Collision.sphereTri(p.x, p.y, p.z, b.radius, tv, ti * 3, tn[ti], tn[ti + 1], tn[ti + 2], contact)) {
                        resolve(iter, dt, 0f, 0f, 0f, false, 0f, 0f)
                        if (contact.ny > 0.7f && ti / 3 < world.triSurf.size && world.triSurf[ti / 3] != 0) surfaceNow = world.triSurf[ti / 3]
                    }
                    ti += 3
                }
            }
        }
        val contacts = contactCount
        contactsLastStep = contacts
        totalContacts += contacts
        stepsRun++
        if (!rotating && abs(v.y) < 0.005f) v.y = 0f

        // Atascada: penetración grave que persiste tras las iteraciones
        var deep = false
        for (i in world.boxes.indices) {
            if (Collision.sphereAabb(p.x, p.y, p.z, b.radius, world.boxes[i], contact) &&
                contact.depth > Config.STUCK_DEPTH
            ) { deep = true; break }
        }
        if (deep) {
            stuckCounter++
            if (stuckCounter >= Config.STUCK_STEPS) {
                resetsByStuck++
                warnListener?.invoke("Bola atascada: se reinicia al punto de inicio")
                resetBall()
                return
            }
        } else stuckCounter = 0

        // 5) rotación visual (rodadura sin deslizamiento): eje = up x v
        val hx = v.x
        val hz = v.z
        val hs = sqrt(hx * hx + hz * hz)
        if (rotating) {
            val vl = v.length()
            val pl = sqrt(p.x * p.x + p.y * p.y + p.z * p.z).coerceAtLeast(1e-4f)
            if (vl > 1e-4f) {
                // eje = up x v con up = hacia el centro de la esfera
                val ux = -p.x / pl; val uy = -p.y / pl; val uz = -p.z / pl
                var ax = uy * v.z - uz * v.y; var ay = uz * v.x - ux * v.z; var az = ux * v.y - uy * v.x
                val al = sqrt(ax * ax + ay * ay + az * az)
                if (al > 1e-5f) {
                    ax /= al; ay /= al; az /= al
                    q.setAxisAngle(ax, ay, az, vl * dt / b.radius)
                    b.rotation.setMul(q, b.rotation).normalize()
                }
            }
        } else if (hs > 1e-4f) {
            // up x v = (0,1,0) x (hx,0,hz) = (hz, 0, -hx)
            val ax = hz / hs
            val az = -hx / hs
            val angle = hs * dt / b.radius
            q.setAxisAngle(ax, 0f, az, angle)
            b.rotation.setMul(q, b.rotation).normalize()
        }

        // 6) victoria
        if (rotating) {
            if (nearFloorPoint(world.goalPosition, world.goalRadius)) won = true
        } else {
            val gdx = p.x - world.goalPosition.x
            val gdz = p.z - world.goalPosition.z
            val gdy = p.y - b.radius - world.goalPosition.y
            if (gdx * gdx + gdz * gdz < world.goalRadius * world.goalRadius &&
                abs(gdy) < Config.GOAL_HEIGHT_TOLERANCE
            ) won = true
        }

        // 6b) checkpoints
        val cps = world.checkpoints
        for (i in checkpointIndex + 1 until cps.size) {
            val c = cps[i]
            val hit = if (rotating) nearFloorPoint(c, 0.6f) else {
                val dx = p.x - c.x
                val dz = p.z - c.z
                dx * dx + dz * dz < Config.CHECKPOINT_RADIUS * Config.CHECKPOINT_RADIUS &&
                    abs(p.y - b.radius - c.y) < Config.CHECKPOINT_HEIGHT_TOL
            }
            if (hit) {
                checkpointIndex = i
                if (rotating) setSpawnFromFloor(i + 1, c) else spawn.set(c.x, c.y + b.radius, c.z)
                checkpointListener?.invoke(i)
            }
        }

        // 7) caída / fuera del tablero
        val m = Config.OUT_OF_BOUNDS_MARGIN
        if (world.offPathDist > 0f && offPath()) fell = true
        if (rotating) return
        val fallY = if (world.fallDrop > 0f) spawn.y - world.fallDrop else Config.FALL_Y
        if (p.y < fallY || p.x < world.minX - m || p.x > world.maxX + m ||
            p.z < world.minZ - m || p.z > world.maxZ + m
        ) fell = true
    }

    private val q = Quat()
    private var pathHint = 0

    /** Esfera giratoria: true si la bola está a menos de [r] de un punto de la pista (el centro de la bola, sobre el suelo). */
    private fun nearFloorPoint(c: Vec3, r: Float): Boolean {
        val l = sqrt(c.x * c.x + c.y * c.y + c.z * c.z).coerceAtLeast(1e-6f)
        val k = ball.radius / l
        val dx = ball.position.x - (c.x - c.x * k)
        val dy = ball.position.y - (c.y - c.y * k)
        val dz = ball.position.z - (c.z - c.z * k)
        return dx * dx + dy * dy + dz * dz < r * r
    }

    /** true si la bola está a más de [World.offPathDist] de la línea central del recorrido (gran esfera). */
    private fun offPath(): Boolean {
        val pts = world.pathPts
        val n = pts.size / 3
        if (n == 0) return false
        val p = ball.position
        val lim = world.offPathDist * world.offPathDist
        var best = Float.MAX_VALUE
        var bi = pathHint
        val lo = maxOf(0, pathHint - 60); val hi = minOf(n - 1, pathHint + 60)
        for (i in lo..hi) {
            val dx = pts[i * 3] - p.x; val dy = pts[i * 3 + 1] - p.y; val dz = pts[i * 3 + 2] - p.z
            val d = dx * dx + dy * dy + dz * dz
            if (d < best) { best = d; bi = i }
        }
        if (best > lim) {   // puede ser un salto de posición (reaparición): búsqueda completa
            for (i in 0 until n) {
                val dx = pts[i * 3] - p.x; val dy = pts[i * 3 + 1] - p.y; val dz = pts[i * 3 + 2] - p.z
                val d = dx * dx + dy * dy + dz * dz
                if (d < best) { best = d; bi = i }
            }
        }
        pathHint = bi
        return best > lim
    }

    /**
     * Respuesta al contacto guardado en [contact]: saca la bola, anula/rebota la velocidad normal
     * (relativa a la superficie, que puede moverse a [bvx],[bvy],[bvz]) y aplica rozamiento de pared.
     */
    private fun resolve(iter: Int, dt: Float, bvx: Float, bvy: Float, bvz: Float, dyn: Boolean, dvx: Float, dvz: Float) {
        val b = ball
        val v = b.velocity
        val p = b.position
        contactCount++
        val nx = contact.nx
        val ny = contact.ny
        val nz = contact.nz
        p.x += nx * contact.depth
        p.y += ny * contact.depth
        p.z += nz * contact.depth
        val vn = (v.x - bvx) * nx + (v.y - bvy) * ny + (v.z - bvz) * nz
        if (vn < 0f) {
            val impact = -vn
            val e = if (impact > Config.REST_THRESHOLD) b.restitution else 0f
            val jn = (1f + e) * impact
            v.x += nx * jn; v.y += ny * jn; v.z += nz * jn
            val floorLike = if (rotating) {
                // suelo de la pista = cara que mira al centro de la esfera
                val pl = sqrt(p.x * p.x + p.y * p.y + p.z * p.z).coerceAtLeast(1e-4f)
                -(nx * p.x + ny * p.y + nz * p.z) / pl > 0.7f
            } else ny > 0.7f
            if (!floorLike) {
                val rvx = v.x - bvx; val rvy = v.y - bvy; val rvz = v.z - bvz
                val vn2 = rvx * nx + rvy * ny + rvz * nz
                val tx = rvx - nx * vn2
                val ty = rvy - ny * vn2
                val tz = rvz - nz * vn2
                val tl = sqrt(tx * tx + ty * ty + tz * tz)
                if (tl > 1e-6f) {
                    val dv = minOf(tl, b.friction * jn)
                    val k = dv / tl
                    v.x -= tx * k; v.y -= ty * k; v.z -= tz * k
                }
                if (impact > Config.IMPACT_MIN_SPEED && iter == 0) {
                    if (impact > maxImpactSeen) maxImpactSeen = impact
                    impactListener?.invoke(impact, nx, nz)
                }
            }
        }
        // una plataforma móvil arrastra a la bola que va encima: la bola hereda la ACELERACIÓN horizontal
        // de la plataforma (agarre perfecto) y su movimiento relativo se amortigua suavemente, de modo que
        // se puede seguir rodando sobre ella con normalidad.
        if (dyn && ny > 0.7f && iter == 0) {
            v.x += dvx
            v.z += dvz
            val k = minOf(1f, 1.5f * dt)
            v.x += (bvx - v.x) * k
            v.z += (bvz - v.z) * k
        }
    }
}
