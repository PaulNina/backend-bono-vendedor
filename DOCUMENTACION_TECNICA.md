# DOCUMENTACIÓN TÉCNICA Y MANUAL DE MÓDULOS DEL SISTEMA
## Sistema de Bonos e Incentivos para Vendedores Skyworth
**Ninabit Solutions | Skyworth Bolivia**  
*Versión:* 1.0.0 (Producción / Release)  
*Fecha:* Septiembre 2026  
*Entornos Productivos:* [bono.skyworth.bo](https://bono.skyworth.bo) / [bono.contabol.com](https://bono.contabol.com)

---

## 1. Resumen Ejecutivo y Arquitectura

El **Sistema de Bono Vendedores Skyworth** es una solución web/móvil concebida para automatizar el control de ventas de televisores y electrodomésticos en tiendas autorizadas, blindar la operación contra seriales duplicados o robados, computar comisiones en Bolivianos (Bs.) y puntos de fidelización, y liquidar pagos bancarios con respaldo digital.

### Arquitectura de 3 Capas:
1. **Frontend SPA / PWA:** React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui y TanStack Query v5.
2. **Backend RESTful:** Java 17, Spring Boot 3.3.6, Spring Security, JWT (0.11.5), Apache POI 5.2.3 y ZXing 3.5.3.
3. **Base de Datos y Archivos:** PostgreSQL 14+ con pool HikariCP y directorio `/uploads` para evidencias fotográficas.

---

## 2. Desglose Exhaustivo de los Módulos del Sistema

---

### 2.1. Módulo de Autenticación, Seguridad y Recuperación de Contraseña
- **Propósito:** Gestión perimetral de acceso seguro mediante tokens criptográficos JWT (JSON Web Tokens).
- **Flujo Operativo:**
  - Login seguro con validación de contraseñas hasheadas mediante algoritmo BCrypt.
  - Generación de token JWT en cabecera `Authorization: Bearer <token>` almacenado en `localStorage`.
  - Mecanismo de recuperación de contraseña: si el usuario olvida su clave, ingresa su correo electrónico registrado. El sistema genera un token de un solo uso con caducidad de 60 minutos y le envía un enlace seguro vía correo (`spring-boot-starter-mail`).
  - Cierre forzado de sesión y redirección a login en caso de token expirado (HTTP 401).

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.1 ]**  
> *Pantalla de Inicio de Sesión (Login) y Formulario de Recuperación de Contraseña*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.2. Módulo de Registro y Solicitudes de Aprobación de Vendedores
- **Propósito:** Onboarding ordenado y fiscalizado de nuevos promotores comerciales.
- **Flujo Operativo:**
  - El vendedor accede a la ruta pública de registro y completa: Cédula de Identidad (CI), teléfono/WhatsApp, correo electrónico, tienda donde trabaja, ciudad y talla de polera corporativa.
  - Al completar el formulario, la cuenta se crea con la bandera `pendingApproval = true` (inactiva para operar).
  - En el panel administrativo (`/admin/registration-requests`), el Administrador visualiza la lista de solicitudes pendientes, verifica los datos del vendedor con la tienda y aprueba o rechaza el acceso con un solo clic.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.2 ]**  
> *Formulario de Registro de Vendedor y Bandeja Administrativa de Solicitudes Pendientes*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.3. Módulo del Portal del Vendedor (Dashboard, Mis Ventas y Mis Pagos)
- **Propósito:** Espacio de trabajo personal y responsivo optimizado para teléfonos móviles en los salones de venta.
- **Vistas Principales:**
  1. **Dashboard:** Muestra tarjetas con métricas en vivo: Total acumulado de Bonos en Bs., Puntos totales vigentes, cantidad de ventas registradas y atajos rápidos.
  2. **Mis Ventas (`/v/mis-ventas`):** Tabla interactiva clasificada por pestañas de estado:
     - **Pendientes:** Ventas que están en la mesa de revisión.
     - **Aprobadas:** Ventas validadas listas para el siguiente lote de liquidación.
     - **Rechazadas:** Ventas observadas por el revisor, mostrando el motivo exacto para subsanar el error.
     - **Cerradas:** Ventas liquidadas y pagadas por el departamento financiero.
  3. **Mis Pagos (`/v/mis-pagos`):** Historial de desembolsos recibidos con fecha, importe en Bs., número de lote y botón para ver el comprobante bancario.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.3 ]**  
> *Dashboard Principal del Vendedor y Sección de Ventas Personales*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.4. Módulo de Registro de Ventas, Validación de Serial y Carga Multimedia
- **Propósito:** Captura segura y ágil de ventas realizadas en el punto comercial.
- **Flujo Operativo:**
  1. **Ingreso y Validación de Serial en Tiempo Real:** El vendedor escribe el número de serie en el campo de texto y hace clic en **"Validar"**. El endpoint `/serials/{serial}/validate` responde al instante indicando si el serial es legítimo, autodetecta el modelo de televisor (ej. Q6600H, G22, etc.) y calcula el bono proyectado y campañas activas.
  2. **Carga de 3 Evidencias Obligatorias:**
     - **Foto 1 (TAG):** Etiqueta trasera del televisor donde se aprecie claramente el código de barras y número de serie.
     - **Foto 2 (Póliza):** Póliza de garantía sellada y fechada por la tienda comercial.
     - **Foto 3 (Nota / Factura):** Recibo de venta o factura entregada al cliente final.
  3. **Optimización de Imágenes:** Transcodifica fotos en formato Apple HEIC a JPEG (`heic2any`) y comprime dinámicamente en canvas HTML5 a resolución máxima de 1600px y calidad 75%, impidiendo saturaciones de red móvil.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.4 ]**  
> *Formulario de Registro de Venta con Validación de Serial y Adjuntos Fotográficos*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.5. Módulo de Campañas Comerciales y Motor de Bonos
- **Propósito:** Configuración de temporadas de incentivos comerciales dinámicas.
- **Flujo Operativo:**
  - El administrador crea una campaña con: Nombre (ej. "Campaña Navidad 2026"), fecha de inicio, fecha de fin y estado activa/inactiva.
  - En la tabla intermedia `campana_productos`, vincula los modelos participantes y define:
    - **Bono en Bs.:** Monto en dinero a pagar al vendedor (ej. 50 Bs., 100 Bs., 150 Bs.).
    - **Puntos de Fidelización:** Puntos asignados por unidad vendida para el ranking.
  - Cuando un vendedor registra una venta, el motor de cálculo evalúa la fecha de venta y asigna los montos correspondientes.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.5 ]**  
> *Panel Administrativo de Campañas y Asignación de Bonos por Modelo de Producto*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.6. Módulo de Mesa de Revisión y Validación Territorial (Workflow Reviewers)
- **Propósito:** Control de calidad visual y prevención de pagos indebidos.
- **Flujo Operativo:**
  - Los usuarios con rol `REVIEWER` están asignados a ciudades específicas (La Paz, Santa Cruz, Cochabamba, etc.).
  - El revisor inspecciona la venta mediante un visor de fotos con zoom (+/-), rotación y visualización comparativa de la etiqueta y la póliza sellada.
  - **Aprobar:** Si los datos coinciden y las fotos son nítidas, aprueba la venta.
  - **Rechazar:** Si la foto está borrosa, el serial no coincide o falta el sello de la tienda, rechaza la venta seleccionando un motivo predefinido o escribiendo una observación. Se archiva en el campo `historial_rechazos`.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.6 ]**  
> *Mesa de Revisión con Visor de Fotos en Alta Definición y Acciones de Aprobación*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.7. Módulo de Gestión de Pagos, Lotes de Liquidación y Comprobantes
- **Propósito:** Liquidación financiera transparente a las cuentas de los vendedores.
- **Flujo Operativo:**
  - Consolida todas las ventas en estado `APROBADA`.
  - Agrupa por vendedor, mostrando el monto total adeudado en Bolivianos (Bs.).
  - Permite descargar la planilla Excel con los datos bancarios y CI de cada vendedor para emitir transferencias masivas.
  - El administrador adjunta el comprobante bancario (recibo de transferencia en PDF o imagen) y liquida el lote, pasando las ventas a estado `CERRADA`.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.7 ]**  
> *Módulo de Liquidación de Bonos y Carga de Comprobantes Bancarios*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.8. Módulo Maestro de Productos y Padrón Oficial de Seriales
- **Propósito:** Mantener el catálogo oficial de televisores y el padrón de equipos importados.
- **Flujo Operativo:**
  - Catálogo de productos organizado por modelo, tamaño en pulgadas y familia tecnológica.
  - Herramienta de importación masiva en formato Excel (Apache POI) para registrar miles de números de serie legítimos despachados al mercado nacional.
  - Control de estado del serial (`DISPONIBLE`, `REGISTRADO`, `ANULADO`).

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.8 ]**  
> *Catálogo de Modelos de Televisores y Padrón de Seriales Importados*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.9. Módulo de Seriales Restringidos (Escudo Anti-Fraude)
- **Propósito:** Impedir el cobro de incentivos por equipos no autorizados, robados o devueltos.
- **Flujo Operativo:**
  - Lista de números de serie bloqueados con fecha y motivo del bloqueo.
  - Si un promotor intenta ingresar un serial de esta lista, el sistema rechaza la operación instantáneamente y registra el incidente en la bitácora de auditoría.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.9 ]**  
> *Lista Negra de Seriales Restringidos y Opciones de Bloqueo/Desbloqueo*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.10. Módulo de Soporte Técnico y Equipos Dañados (Escáner de Código de Barras)
- **Propósito:** Registro y seguimiento de garantías por defectos técnicos o roturas, incorporando la tecnología de escaneo de código de barras.
- **Flujo Operativo:**
  - Acceso para usuarios con rol `TECNICO`.
  - **Escáner con Cámara (ZXing):** Integra el componente `BarcodeTagScanner` que permite al técnico apuntar la cámara web o móvil directamente al código de barras de la etiqueta trasera para autocompletar el serial.
  - **Decodificación en Backend:** Además, el servicio `EquipoDanadoService` decodifica códigos de barras directamente desde las imágenes subidas aplicando rotaciones automáticas a 0°, 90°, 180° y 270°.
  - Registro del televisor con falla, datos del cliente, fotografías del daño físico o electrónico, y clasificación según el catálogo de fallas (panel, tarjeta madre, backlight, audio, etc.).

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.10 ]**  
> *Registro de Equipos Dañados con Escáner de Código de Barras y Catálogo de Fallas*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.11. Módulo de Gamificación, Leaderboard y Ranking de Vendedores
- **Propósito:** Estimular el volumen de colocación de producto mediante incentivos competitivos.
- **Flujo Operativo:**
  - Tabla de posiciones actualizada en tiempo real según ventas aprobadas y puntos acumulados en el mes.
  - Reconocimiento con medallas virtuales (Top 1, Top 2, Top 3) y segmentación por ciudad y tienda.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.11 ]**  
> *Leaderboard y Ranking de Rendimiento Comercial de Vendedores*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.12. Módulo de Métricas, Reportes y Auditoría
- **Propósito:** Monitoreo ejecutivo y trazabilidad total de operaciones sensibles.
- **Flujo Operativo:**
  - Gráficos estadísticos con Recharts: Distribución de ventas por ciudad, ventas por tienda y evolución temporal.
  - Bitácora inmutable de auditoría: registro de usuario, acción, entidad afectada, dirección IP y fecha/hora exacta.
  - Exportación de reportes ejecutivos a formato Microsoft Excel.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.12 ]**  
> *Dashboard de Métricas KPI y Tabla de Auditoría Inmutable*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

### 2.13. Módulo de Configuración Global y Popups de Notificación
- **Propósito:** Parametrización en caliente del comportamiento de la plataforma.
- **Flujo Operativo:**
  - Gestión de anuncios Popup interactivos con imagen y texto que se muestran a los vendedores al iniciar sesión.
  - Configuración de correos receptores de alertas del sistema.
  - Ajuste de políticas de fechas: número máximo de semanas retroactivas permitidas para registrar ventas.

> **[ 📷 ESPACIO PARA CAPTURA DE PANTALLA: Figura 2.13 ]**  
> *Configuración Global del Sistema y Publicación de Anuncios Popups*  
> *(Pegue aquí su captura de pantalla tomada en el navegador)*

---

## 3. Guía para Pegar Capturas en Microsoft Word

Para incorporar sus capturas en el archivo Word generado:
1. Abra el archivo `Documentacion_Tecnica_Modulos_Skyworth_Bono.docx` en Microsoft Word.
2. Tome la captura de pantalla de su sistema (`Windows + Shift + S`).
3. Haga clic dentro del recuadro punteado gris correspondiente a la figura deseada.
4. Presione **`Ctrl + V`** (o use el menú *Insertar > Imágenes > Este dispositivo*).
5. La imagen se ajustará automáticamente al ancho del recuadro.

---

## 4. Catálogo Sintetizado de APIs REST (Matriz Ejecutiva)

A fin de mantener la documentación ágil, ejecutiva y fácil de consultar, los **123 endpoints** del sistema se consolidan en la siguiente **Matriz Ejecutiva de Recursos REST**:

| Módulo / Recurso | URI Base | Métodos HTTP | Rol Mínimo | Alcance y Operaciones Clave |
| :--- | :--- | :--- | :--- | :--- |
| **Autenticación y Cuentas** | `/api/auth` | `POST`, `PUT` | Público / Autenticado | Login JWT, registro de vendedores, reseteo de clave por email (token de 60 min) y cambio de contraseña. |
| **Usuarios y Roles (RBAC)** | `/api/users` | `GET`, `POST`, `DELETE` | `ADMIN` | Administración de usuarios internos (Admin, Reviewer, Supervisor, Técnico), asignación de roles y bajas. |
| **Gestión de Vendedores** | `/api/vendors` | `GET`, `POST`, `PUT`, `PATCH` | `ADMIN`, `REVIEWER` | Padrón de promotores, aprobación/rechazo de registro, activación, edición de perfil y estadísticas por ciudad. |
| **Portal del Vendedor** | `/api/vendor` | `GET`, `PUT` | `VENDOR` | Perfil del vendedor en sesión (`/me`), saldo acumulado de bonos en Bs., puntos (`/stats`) e historial de ventas (`/sales`). |
| **Registro y Control de Ventas** | `/api/sales` | `GET`, `POST`, `PUT`, `DELETE` | `VENDOR`, `REVIEWER`, `ADMIN` | Alta de ventas con fotos (Multipart), mesa de revisión (aprobación/rechazo justificado), reversión y exportación a Excel. |
| **Padrón de Seriales** | `/api/serials` | `GET`, `POST`, `PUT`, `DELETE` | `ADMIN`, Autenticado | Validación en tiempo real (`/validate`), importación masiva en Excel (Apache POI), estadísticas y exportes. |
| **Escudo Anti-Fraude** | `/api/restricted-serials` | `GET`, `POST`, `DELETE` | `ADMIN`, `REVIEWER` | Lista negra de seriales bloqueados (por robo, daño o fraude), bloqueo masivo y desbloqueo. |
| **Campañas Comerciales** | `/api/campaigns` | `GET`, `POST`, `PUT`, `DELETE` | `ADMIN`, Autenticado | Configuración de temporadas de incentivos, vigencias, y asignación de bonos en Bs. y puntos por modelo de televisor. |
| **Pagos y Liquidaciones** | `/api/payments` | `GET`, `POST` | `ADMIN`, `VENDOR` | Corte contable de bonos aprobados, pago bancario con subida de comprobante y exportación de nómina a Excel. |
| **Catálogo de Productos** | `/api/products`, `/product-types` | `GET`, `POST`, `PUT`, `DELETE` | `ADMIN`, Autenticado | Maestro de modelos de Smart TV Skyworth, pulgadas, familias tecnológicas (QLED/UHD) y estados de activación. |
| **Tiendas y Zonificación** | `/api/tiendas`, `/cities`, `/city-groups` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` | `ADMIN`, `REVIEWER` | Catálogo de tiendas autorizadas, padrón de ciudades y grupos territoriales para asignación a supervisores. |
| **Servicio Técnico / Dañados** | `/api/admin/equipos-danados` | `GET`, `POST`, `PUT`, `DELETE` | `TECNICO`, `ADMIN` | Registro de televisores averiados con fotos, catálogo de fallas, clientes, garantías y escáner ZXing de códigos de barra. |
| **Gamificación y Reportes** | `/api/ranking`, `/reports` | `GET`, `POST` | `ADMIN`, `SUPERVISOR`, Autenticado | Leaderboard de ventas mensual en vivo, disparo manual de reportes por correo y envíos automáticos semanales. |
| **Popups y Notificaciones** | `/api/admin/popups`, `/email-recipients` | `GET`, `POST`, `PUT`, `DELETE` | `ADMIN`, Público | Gestión de anuncios emergentes (banners informativos para vendedores) y lista de correos receptores de alertas. |
| **Configuración y Auditoría** | `/api/admin/config`, `/audit`, `/dashboard` | `GET`, `PUT` | `ADMIN`, `SUPERVISOR` | Parámetros globales (semanas límite de venta), matriz de permisos, métricas KPI y bitácora inmutable de eventos. |

> **[ ℹ️ DOCUMENTACIÓN INTERACTIVA ABIERTA: SWAGGER / OPENAPI ]**  
> Para consultar la especificación técnica atómica de cada uno de los 123 endpoints (cuerpos JSON de petición/respuesta, códigos de error HTTP y parámetros de consulta), el backend cuenta con SpringDoc OpenAPI activo en tiempo de ejecución:  
> - **Swagger UI:** `http://localhost:7001/api/swagger-ui.html` (o en producción `/api/swagger-ui.html`)  
> - **Especificación JSON OpenAPI:** `/api/v3/api-docs`

---

## 5. Guía de Despliegue y Puesta en Marcha

### 5.1. Requisitos de Servidor
- **Sistema Operativo:** Ubuntu Server 22.04 LTS (o Windows Server 2019/2022).
- **Java:** OpenJDK 17 de 64 bits.
- **Node.js:** v18.x o v20.x LTS con npm 9+.
- **Base de Datos:** PostgreSQL 14, 15 o 16 con codificación UTF-8.
- **Hardware Recomendado:** Mínimo 4 GB de RAM (8 GB en producción) y 40 GB SSD.

### 5.2. Despliegue del Backend (Spring Boot 3)
```bash
cd backend_bono_vendedor
# 1. Compilación del artefacto JAR ejecutable
mvn clean package -DskipTests

# 2. Iniciar el servicio en segundo plano (puerto 7001, context-path /api)
java -jar target/bono-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 5.3. Despliegue del Frontend (React + Vite)
```bash
cd frontend_bono_vendedor3
# 1. Configurar variable de entorno en .env
# VITE_API_URL=https://bono.skyworth.bo/api

# 2. Instalar dependencias y empaquetar
npm install
npm run build

# 3. Publicar la carpeta dist/ en Nginx o Apache con soporte de enrutamiento SPA
```

---

## 6. Mantenimiento, Copias de Seguridad y Bitácoras

### 6.1. Política de Bitácora Rotativa (Logs)
- **Ruta de log activo:** `logs/skyworth.log`
- **Tamaño de rotación:** 10 MB por archivo.
- **Compresión automática:** GZIP (`logs/skyworth-YYYY-MM-DD.i.log.gz`).
- **Retención histórica:** 30 días continuos con depuración automática para evitar saturación de disco.

### 6.2. Estrategia de Copias de Seguridad (Backups Diarios)
1. **Base de Datos:** `pg_dump -U postgres -d db_bono_skyworth -F c -b -v -f /backups/db_skyworth_$(date +%Y%m%d).dump`
2. **Archivos Multimedia:** `tar -czf /backups/uploads_skyworth_$(date +%Y%m%d).tar.gz backend_bono_vendedor/uploads/`

---

## 7. Guía de Solución de Problemas Frecuentes (Troubleshooting)

| Incidencia Reportada | Causa Probable | Acción de Solución Recomendada |
| :--- | :--- | :--- |
| **Serial no reconocido o inválido** | El vendedor ingresa un serial y la app indica *"No válido"*. | Verificar que el serial esté cargado en el padrón oficial (`/api/serials`) y que no figure en seriales restringidos ni registrado previamente. |
| **Error 401 Unauthorized** | Redirección automática al login al intentar registrar una venta. | El token JWT ha expirado. Por seguridad el frontend expulsa la sesión. Iniciar sesión nuevamente. |
| **Fotos de iPhone (formato HEIC)** | El vendedor adjunta fotos y no se visualizan. | El sistema integra `heic2any` para convertir HEIC a JPG en el cliente. Si el celular es antiguo, se recomienda configurar la cámara del iPhone en *"El más compatible"*. |
| **Agotamiento del pool de base de datos** | Respuestas lentas o errores 500 en horas de alta concurrencia. | HikariCP está configurado en 10 conexiones. Para alta carga, incrementar `spring.datasource.hikari.maximum-pool-size=25` en `application.properties`. |
| **Emails de reseteo no llegan** | Los usuarios no reciben el correo de cambio de clave. | Probar la conectividad del servidor SMTP mediante el endpoint `GET /api/admin/config/test-smtp`. |

---

## 8. Glosario de Términos y Control de Versiones

### 8.1. Glosario del Negocio
- **TAG:** Etiqueta trasera de fábrica del televisor con modelo y código de barras.
- **Póliza Sellada:** Comprobante de garantía sellado por el punto de venta comercial autorizado.
- **Venta CERRADA:** Venta aprobada cuyo incentivo económico ya fue transferido y liquidado con comprobante bancario.
- **RBAC:** Control de acceso basado en roles (Admin, Reviewer, Supervisor, Vendedor, Técnico).

### 8.2. Historial de Revisiones del Documento
| Versión | Fecha | Autor | Resumen de Cambios |
| :---: | :---: | :---: | :--- |
| **v1.0.0** | Septiembre 2026 | Ninabit Solutions | Emisión inicial de arquitectura y especificación técnica. |
| **v1.0.1** | Septiembre 2026 | Ninabit Solutions | Inclusión de placeholders para capturas de pantalla y precisión en módulos de escaneo. |
| **v1.0.2** | Septiembre 2026 | Ninabit Solutions | Matriz ejecutiva de APIs REST (123 endpoints), guías de despliegue, backups y troubleshooting. |

### 8.3. Aceptación y Firmas del Proyecto

```
_____________________________________          _____________________________________
  RESPONSABLE TÉCNICO DE DESARROLLO              RESPONSABLE COMERCIAL / NEGOCIO
          Ninabit Solutions                              Skyworth Bolivia
```



