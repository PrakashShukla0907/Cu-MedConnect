-- CU MedConnect database schema (MySQL 8+)
-- Sample hospitals/doctors below are fictional demo records, not real availability.

CREATE TABLE IF NOT EXISTS patients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    blood_group VARCHAR(5) NULL,
    emergency_contact_name VARCHAR(120) NULL,
    emergency_contact_phone VARCHAR(20) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_patients_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS hospitals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    emergency_services_available BOOLEAN NOT NULL DEFAULT FALSE,
    availability_updated_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    INDEX idx_hospitals_city (city)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS doctors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    INDEX idx_doctors_specialization (specialization),
    CONSTRAINT fk_doctors_hospital
        FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_at DATETIME NOT NULL,
    consultation_mode ENUM('VIDEO', 'IN_PERSON') NOT NULL DEFAULT 'VIDEO',
    reason VARCHAR(500) NOT NULL,
    status ENUM('REQUESTED', 'CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'REQUESTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_doctor_appointment_slot UNIQUE (doctor_id, appointment_at),
    INDEX idx_appointments_patient_date (patient_id, appointment_at),
    CONSTRAINT fk_appointments_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_appointments_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS medical_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    record_type VARCHAR(60) NOT NULL,
    title VARCHAR(180) NOT NULL,
    summary TEXT NOT NULL,
    doctor_name VARCHAR(120) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_records_patient_date (patient_id, created_at),
    CONSTRAINT fk_records_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS emergency_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    description VARCHAR(500) NOT NULL,
    status ENUM('CREATED', 'ACKNOWLEDGED', 'CLOSED') NOT NULL DEFAULT 'CREATED',
    idempotency_key VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_emergency_patient_idempotency UNIQUE (patient_id, idempotency_key),
    INDEX idx_emergency_status_date (status, created_at),
    CONSTRAINT fk_emergencies_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- Demo hospital and clinic listings; names, partners, and availability are not verified.
INSERT INTO hospitals
    (id, name, city, address, emergency_services_available, availability_updated_at)
VALUES
    (1, 'Chandigarh University Healthcare & Wellness Center', 'Mohali', 'Campus Medical Wing, Sector Gharuan', TRUE, CURRENT_TIMESTAMP),
    (2, 'Max Super Speciality Hospital', 'Mohali', 'Phase 6, Near Civil Hospital', TRUE, CURRENT_TIMESTAMP),
    (3, 'Fortis Hospital Mohali', 'Mohali', 'Sector 62, Phase 8', TRUE, CURRENT_TIMESTAMP),
    (4, 'Post Graduate Institute of Medical Education & Research (PGIMER)', 'Chandigarh', 'Madhya Marg, Sector 12', TRUE, CURRENT_TIMESTAMP),
    (5, 'Government Multi-Specialty Hospital (GMSH)', 'Chandigarh', 'Sector 16', TRUE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), city = VALUES(city), address = VALUES(address), emergency_services_available = VALUES(emergency_services_available);

INSERT INTO doctors (id, hospital_id, full_name, specialization, active)
VALUES
    (1, 1, 'Dr. Harpreet Kaur Gill', 'General Medicine & Campus Physician', TRUE),
    (2, 2, 'Dr. Rajesh Mehra', 'Cardiology & Critical Care', TRUE),
    (3, 3, 'Dr. Sandeep Aggarwal', 'Orthopedics & Sports Medicine', TRUE),
    (4, 4, 'Dr. Anjna Sharma', 'Emergency Medicine & Trauma Care', TRUE),
    (5, 1, 'Dr. Anjna Sharma', 'Internal Medicine', TRUE),
    (6, 5, 'Dr. Neha Bhasin', 'Dermatology & Skin Care', TRUE)
ON DUPLICATE KEY UPDATE
    hospital_id = VALUES(hospital_id), full_name = VALUES(full_name), specialization = VALUES(specialization), active = VALUES(active);

-- Default registered patients (Includes project developers and student profiles)
INSERT INTO patients (id, full_name, email, phone, blood_group, emergency_contact_name, emergency_contact_phone)
VALUES
    (1, 'Pratik Vats', 'pratik.vats@cumedconnect.in', '+91-9876543210', 'B+', 'Anand Vats', '+91-9876543211'),
    (2, 'Aditya Singh', 'aditya.singh@cumedconnect.in', '+91-9876543219', 'O+', 'Vikram Singh', '+91-9876543218'),
    (3, 'Prakash Shukla', 'prakash.shukla@cumedconnect.in', '+91-9876543220', 'A+', 'Ramesh Shukla', '+91-9876500010'),
    (4, 'Shitanshu Shekhar', 'shitanshu.shekhar@cumedconnect.in', '+91-9876543221', 'AB+', 'Suresh Shekhar', '+91-9876500011'),
    (5, 'Tarun Sharma', 'tarun.sharma@cumedconnect.in', '+91-9876543222', 'O+', 'Rajesh Sharma', '+91-9876500012'),
    (6, 'Priya Patel', 'priya.patel@cumedconnect.in', '+91-9876543223', 'B+', 'Kiran Patel', '+91-9876500013'),
    (7, 'Rahul Verma', 'rahul.verma@cumedconnect.in', '+91-9876543224', 'A+', 'Sunita Verma', '+91-9876500014'),
    (8, 'Simran Kaur', 'simran.kaur@cumedconnect.in', '+91-9876543225', 'AB+', 'Gurdeep Singh', '+91-9876500015')
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    email = VALUES(email),
    phone = VALUES(phone),
    blood_group = VALUES(blood_group),
    emergency_contact_name = VALUES(emergency_contact_name),
    emergency_contact_phone = VALUES(emergency_contact_phone);

