package com.cumedconnect;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MedConnectApi {

    private final JdbcTemplate jdbc;

    public MedConnectApi(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------- PATIENTS --------------------

    @PostMapping("/patients")
    public ResponseEntity<PatientView> registerPatient(@Valid @RequestBody PatientRequest request) {
        try {
            long id = insertAndGetId(
                    "INSERT INTO patients (full_name, email, phone, blood_group, " +
                            "emergency_contact_name, emergency_contact_phone) VALUES (?, ?, ?, ?, ?, ?)",
                    ps -> {
                        ps.setString(1, request.fullName());
                        ps.setString(2, request.email().trim().toLowerCase());
                        ps.setString(3, request.phone());
                        ps.setString(4, request.bloodGroup());
                        ps.setString(5, request.emergencyContactName());
                        ps.setString(6, request.emergencyContactPhone());
                    }
            );
            return ResponseEntity.created(URI.create("/api/patients/" + id)).body(findPatient(id));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
    }

    @GetMapping("/patients/{patientId}")
    public PatientView getPatient(@PathVariable long patientId) {
        return findPatient(patientId);
    }

    @GetMapping("/patients")
    public List<PatientView> getPatients(@RequestParam(required = false) String email) {
        if (email != null && !email.isBlank()) {
            return jdbc.query(
                    "SELECT id, full_name, email, phone, blood_group, emergency_contact_name, " +
                            "emergency_contact_phone, created_at FROM patients WHERE LOWER(email) = LOWER(?)",
                    (rs, row) -> new PatientView(
                            rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                            rs.getString("phone"), rs.getString("blood_group"),
                            rs.getString("emergency_contact_name"), rs.getString("emergency_contact_phone"),
                            toLocalDateTime(rs.getTimestamp("created_at"))
                    ), email.trim()
            );
        }
        return jdbc.query(
                "SELECT id, full_name, email, phone, blood_group, emergency_contact_name, " +
                        "emergency_contact_phone, created_at FROM patients ORDER BY id DESC LIMIT 50",
                (rs, row) -> new PatientView(
                        rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("phone"), rs.getString("blood_group"),
                        rs.getString("emergency_contact_name"), rs.getString("emergency_contact_phone"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                )
        );
    }

    // -------------------- HOSPITALS AND DOCTORS --------------------

    @GetMapping("/hospitals")
    public List<HospitalView> getHospitals(@RequestParam(required = false) String city) {
        String sql = "SELECT id, name, city, address, emergency_services_available, availability_updated_at " +
                "FROM hospitals";
        if (city == null || city.isBlank()) {
            sql += " ORDER BY name";
            return jdbc.query(sql, (rs, row) -> new HospitalView(
                    rs.getLong("id"), rs.getString("name"), rs.getString("city"),
                    rs.getString("address"), rs.getBoolean("emergency_services_available"),
                    toLocalDateTime(rs.getTimestamp("availability_updated_at"))
            ));
        }

        sql += " WHERE LOWER(city) = LOWER(?) ORDER BY name";
        return jdbc.query(sql, (rs, row) -> new HospitalView(
                rs.getLong("id"), rs.getString("name"), rs.getString("city"),
                rs.getString("address"), rs.getBoolean("emergency_services_available"),
                toLocalDateTime(rs.getTimestamp("availability_updated_at"))
        ), city.trim());
    }

    @GetMapping("/doctors")
    public List<DoctorView> getDoctors(@RequestParam(required = false) String specialization) {
        String sql = "SELECT d.id, d.full_name, d.specialization, d.hospital_id, h.name AS hospital_name " +
                "FROM doctors d JOIN hospitals h ON h.id = d.hospital_id WHERE d.active = TRUE";
        if (specialization == null || specialization.isBlank()) {
            sql += " ORDER BY d.full_name";
            return jdbc.query(sql, (rs, row) -> new DoctorView(
                    rs.getLong("id"), rs.getString("full_name"), rs.getString("specialization"),
                    rs.getLong("hospital_id"), rs.getString("hospital_name")
            ));
        }

        sql += " AND LOWER(d.specialization) LIKE LOWER(?) ORDER BY d.full_name";
        return jdbc.query(sql, (rs, row) -> new DoctorView(
                rs.getLong("id"), rs.getString("full_name"), rs.getString("specialization"),
                rs.getLong("hospital_id"), rs.getString("hospital_name")
        ), "%" + specialization.trim() + "%");
    }

    // -------------------- APPOINTMENTS --------------------

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentView> bookAppointment(@Valid @RequestBody AppointmentRequest request) {
        requireExists("SELECT COUNT(*) FROM patients WHERE id = ?", request.patientId(), "Patient not found.");
        requireExists("SELECT COUNT(*) FROM doctors WHERE id = ? AND active = TRUE", request.doctorId(),
                "Active doctor not found.");

        try {
            long id = insertAndGetId(
                    "INSERT INTO appointments (patient_id, doctor_id, appointment_at, consultation_mode, reason) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    ps -> {
                        ps.setLong(1, request.patientId());
                        ps.setLong(2, request.doctorId());
                        ps.setTimestamp(3, Timestamp.valueOf(request.appointmentAt()));
                        ps.setString(4, request.consultationMode());
                        ps.setString(5, request.reason());
                    }
            );
            return ResponseEntity.created(URI.create("/api/appointments/" + id)).body(findAppointment(id));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This doctor already has an appointment at that exact time. Choose another slot.");
        }
    }

    @GetMapping("/appointments/{appointmentId}")
    public AppointmentView getAppointment(@PathVariable long appointmentId) {
        return findAppointment(appointmentId);
    }

    @GetMapping("/appointments")
    public List<AppointmentView> getAppointments(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.patient_id, a.doctor_id, d.full_name AS doctor_name, " +
                        "h.name AS hospital_name, a.appointment_at, a.consultation_mode, a.reason, a.status " +
                        "FROM appointments a JOIN doctors d ON d.id = a.doctor_id " +
                        "JOIN hospitals h ON h.id = d.hospital_id WHERE 1=1"
        );
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (patientId != null) {
            sql.append(" AND a.patient_id = ?");
            params.add(patientId);
        }
        if (doctorId != null) {
            sql.append(" AND a.doctor_id = ?");
            params.add(doctorId);
        }
        sql.append(" ORDER BY a.appointment_at DESC");
        return jdbc.query(sql.toString(), (rs, row) -> new AppointmentView(
                rs.getLong("id"), rs.getLong("patient_id"), rs.getLong("doctor_id"),
                rs.getString("doctor_name"), rs.getString("hospital_name"),
                rs.getTimestamp("appointment_at").toLocalDateTime(),
                rs.getString("consultation_mode"), rs.getString("reason"), rs.getString("status")
        ), params.toArray());
    }

    // -------------------- MEDICAL RECORDS --------------------

    @PostMapping("/patients/{patientId}/records")
    public ResponseEntity<MedicalRecordView> addMedicalRecord(
            @PathVariable long patientId,
            @Valid @RequestBody MedicalRecordRequest request) {
        requireExists("SELECT COUNT(*) FROM patients WHERE id = ?", patientId, "Patient not found.");

        long id = insertAndGetId(
                "INSERT INTO medical_records (patient_id, record_type, title, summary, doctor_name) " +
                        "VALUES (?, ?, ?, ?, ?)",
                ps -> {
                    ps.setLong(1, patientId);
                    ps.setString(2, request.recordType());
                    ps.setString(3, request.title());
                    ps.setString(4, request.summary());
                    ps.setString(5, request.doctorName());
                }
        );

        MedicalRecordView record = findMedicalRecord(id);
        return ResponseEntity.created(URI.create("/api/patients/" + patientId + "/records/" + id)).body(record);
    }

    @GetMapping("/patients/{patientId}/records")
    public List<MedicalRecordView> getMedicalRecords(@PathVariable long patientId) {
        requireExists("SELECT COUNT(*) FROM patients WHERE id = ?", patientId, "Patient not found.");
        return jdbc.query(
                "SELECT id, patient_id, record_type, title, summary, doctor_name, created_at " +
                        "FROM medical_records WHERE patient_id = ? ORDER BY created_at DESC",
                (rs, row) -> new MedicalRecordView(
                        rs.getLong("id"), rs.getLong("patient_id"), rs.getString("record_type"),
                        rs.getString("title"), rs.getString("summary"), rs.getString("doctor_name"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ), patientId
        );
    }

    // -------------------- EMERGENCY SOS --------------------

    @PostMapping("/emergencies")
    public ResponseEntity<EmergencySubmission> createEmergency(
            @Valid @RequestBody EmergencyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (!idempotencyKey.matches("[A-Za-z0-9._-]{8,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 8-80 characters: letters, numbers, dot, underscore or hyphen.");
        }

        requireExists("SELECT COUNT(*) FROM patients WHERE id = ?", request.patientId(), "Patient not found.");

        // INSERT IGNORE + a patient-scoped unique key prevents duplicate SOS records on client retries.
        int inserted = jdbc.update(
                "INSERT IGNORE INTO emergency_requests " +
                        "(patient_id, latitude, longitude, description, idempotency_key) VALUES (?, ?, ?, ?, ?)",
                request.patientId(), request.latitude(), request.longitude(), request.description(), idempotencyKey
        );

        EmergencyView emergency = jdbc.query(
                "SELECT id, patient_id, latitude, longitude, description, status, created_at " +
                        "FROM emergency_requests WHERE patient_id = ? AND idempotency_key = ?",
                (rs, row) -> new EmergencyView(
                        rs.getLong("id"), rs.getLong("patient_id"), rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"), rs.getString("description"), rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ), request.patientId(), idempotencyKey
        ).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save emergency request."));

        EmergencySubmission response = new EmergencySubmission(
                emergency,
                "112",
                "NOT_DISPATCHED",
                "This prototype records the request only; it does not contact campus responders or emergency services. Call 112 for immediate assistance."
        );

        return ResponseEntity.status(inserted == 1 ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @GetMapping("/emergencies/{emergencyId}")
    public EmergencyView getEmergency(@PathVariable long emergencyId) {
        return jdbc.query(
                "SELECT id, patient_id, latitude, longitude, description, status, created_at " +
                        "FROM emergency_requests WHERE id = ?",
                (rs, row) -> new EmergencyView(
                        rs.getLong("id"), rs.getLong("patient_id"), rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"), rs.getString("description"), rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ), emergencyId
        ).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency request not found."));
    }

    // -------------------- PRIVATE HELPERS --------------------

    private PatientView findPatient(long id) {
        return jdbc.query(
                "SELECT id, full_name, email, phone, blood_group, emergency_contact_name, " +
                        "emergency_contact_phone, created_at FROM patients WHERE id = ?",
                (rs, row) -> new PatientView(
                        rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("phone"), rs.getString("blood_group"),
                        rs.getString("emergency_contact_name"), rs.getString("emergency_contact_phone"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ), id
        ).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found."));
    }

    private AppointmentView findAppointment(long id) {
        return jdbc.query(
                "SELECT a.id, a.patient_id, a.doctor_id, d.full_name AS doctor_name, " +
                        "h.name AS hospital_name, a.appointment_at, a.consultation_mode, a.reason, a.status " +
                        "FROM appointments a JOIN doctors d ON d.id = a.doctor_id " +
                        "JOIN hospitals h ON h.id = d.hospital_id WHERE a.id = ?",
                (rs, row) -> new AppointmentView(
                        rs.getLong("id"), rs.getLong("patient_id"), rs.getLong("doctor_id"),
                        rs.getString("doctor_name"), rs.getString("hospital_name"),
                        rs.getTimestamp("appointment_at").toLocalDateTime(),
                        rs.getString("consultation_mode"), rs.getString("reason"), rs.getString("status")
                ), id
        ).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found."));
    }

    private MedicalRecordView findMedicalRecord(long id) {
        return jdbc.query(
                "SELECT id, patient_id, record_type, title, summary, doctor_name, created_at " +
                        "FROM medical_records WHERE id = ?",
                (rs, row) -> new MedicalRecordView(
                        rs.getLong("id"), rs.getLong("patient_id"), rs.getString("record_type"),
                        rs.getString("title"), rs.getString("summary"), rs.getString("doctor_name"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ), id
        ).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical record not found."));
    }

    private void requireExists(String sql, long id, String message) {
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }

    private long insertAndGetId(String sql, StatementSetter setter) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            setter.set(statement);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated ID.");
        }
        return key.longValue();
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    @FunctionalInterface
    private interface StatementSetter {
        void set(PreparedStatement statement) throws SQLException;
    }

    // -------------------- REQUEST / RESPONSE TYPES --------------------

    public record PatientRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = "^[+]?[0-9 -]{8,18}$") String phone,
            @Size(max = 5) String bloodGroup,
            @Size(max = 120) String emergencyContactName,
            @Pattern(regexp = "^[+]?[0-9 -]{8,18}$") String emergencyContactPhone
    ) {}

    public record PatientView(
            long id, String fullName, String email, String phone, String bloodGroup,
            String emergencyContactName, String emergencyContactPhone, LocalDateTime createdAt
    ) {}

    public record HospitalView(
            long id, String name, String city, String address,
            boolean emergencyServicesAvailable, LocalDateTime availabilityUpdatedAt
    ) {}

    public record DoctorView(
            long id, String fullName, String specialization, long hospitalId, String hospitalName
    ) {}

    public record AppointmentRequest(
            @NotNull @Positive Long patientId,
            @NotNull @Positive Long doctorId,
            @NotNull @Future LocalDateTime appointmentAt,
            @NotBlank @Pattern(regexp = "VIDEO|IN_PERSON") String consultationMode,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record AppointmentView(
            long id, long patientId, long doctorId, String doctorName, String hospitalName,
            LocalDateTime appointmentAt, String consultationMode, String reason, String status
    ) {}

    public record MedicalRecordRequest(
            @NotBlank @Size(max = 60) String recordType,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 5000) String summary,
            @Size(max = 120) String doctorName
    ) {}

    public record MedicalRecordView(
            long id, long patientId, String recordType, String title,
            String summary, String doctorName, LocalDateTime createdAt
    ) {}

    public record EmergencyRequest(
            @NotNull @Positive Long patientId,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @NotBlank @Size(max = 500) String description
    ) {}

    public record EmergencyView(
            long id, long patientId, BigDecimal latitude, BigDecimal longitude,
            String description, String status, LocalDateTime createdAt
    ) {}

    public record EmergencySubmission(
            EmergencyView emergency,
            String emergencyNumber,
            String dispatchStatus,
            String userInstruction
    ) {}
}
