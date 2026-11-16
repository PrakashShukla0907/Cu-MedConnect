// CU MedConnect Shared JavaScript Module

function getSession() {
  const data = localStorage.getItem('cu_medconnect_session');
  if (!data) return null;
  try {
    return JSON.parse(data);
  } catch(e) {
    return null;
  }
}

function setSession(role, userObj) {
  const session = { role, data: userObj };
  localStorage.setItem('cu_medconnect_session', JSON.stringify(session));
  return session;
}

function clearSession() {
  localStorage.removeItem('cu_medconnect_session');
  window.location.href = '/index.html';
}

function showToast(msg) {
  let t = document.getElementById('toast');
  if (!t) {
    t = document.createElement('div');
    t.id = 'toast';
    t.className = 'toast';
    document.body.appendChild(t);
  }
  t.innerText = msg;
  t.style.display = 'block';
  setTimeout(() => t.style.display = 'none', 3500);
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function renderHeaderNav(activePage = '') {
  const session = getSession();
  const authDiv = document.getElementById('auth-actions');
  const navContainer = document.getElementById('global-nav-links');

  if (navContainer) {
    let linksHtml = `
      <a href="/index.html" class="nav-link ${activePage === 'home' ? 'active' : ''}">Home</a>
      <a href="/hospitals.html" class="nav-link ${activePage === 'hospitals' ? 'active' : ''}">Hospitals</a>
      <a href="/doctors.html" class="nav-link ${activePage === 'doctors' ? 'active' : ''}">Doctors</a>
    `;

    if (session) {
      if (session.role === 'patient') {
        linksHtml += `<a href="/patient-dashboard.html" class="nav-link ${activePage === 'patient' ? 'active' : ''}">Patient Portal</a>`;
      } else if (session.role === 'doctor') {
        linksHtml += `<a href="/doctor-dashboard.html" class="nav-link ${activePage === 'doctor' ? 'active' : ''}">Doctor Portal</a>`;
      }
    }
    navContainer.innerHTML = linksHtml;
  }

  if (authDiv) {
    if (session) {
      const roleLabel = session.role === 'doctor' ? 'Doctor' : 'Patient';
      const roleClass = session.role === 'doctor' ? 'doctor' : '';
      const name = session.data.fullName || session.data.name || 'User';

      authDiv.innerHTML = `
        <div class="user-pill">
          <span>👤 ${escapeHtml(name)}</span>
          <span class="role-badge ${roleClass}">${roleLabel}</span>
        </div>
        <button class="logout-btn" onclick="clearSession()">Logout</button>
      `;
    } else {
      authDiv.innerHTML = `
        <a href="/index.html" class="btn btn-primary" style="padding:0.45rem 0.9rem; font-size:0.825rem;">Choose profile</a>
      `;
    }
  }
}

function openSosModal() {
  const modal = document.getElementById('sos-modal');
  if (modal) {
    const session = getSession();
    if (session && session.role === 'patient') {
      const pInput = document.getElementById('sos-patientId');
      if (pInput) pInput.value = session.data.id;
    }
    modal.classList.add('open');
  }
}

function closeSosModal() {
  const modal = document.getElementById('sos-modal');
  if (modal) modal.classList.remove('open');
}

async function handleSendSos(e) {
  e.preventDefault();
  const patientId = document.getElementById('sos-patientId').value;
  const latitude = document.getElementById('sos-lat').value;
  const longitude = document.getElementById('sos-lng').value;
  const description = document.getElementById('sos-desc').value;
  const idempotencyKey = 'sos-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6);

  try {
    const res = await fetch('/api/emergencies', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify({ patientId, latitude, longitude, description })
    });
    const data = await res.json();
    const resDiv = document.getElementById('sos-result');
    resDiv.style.display = 'block';
    resDiv.innerHTML = `
      <div style="background:#fee2e2; border:1px solid #fecaca; color:#b91c1c; padding:1rem; border-radius:8px; font-size:0.875rem;">
        🚨 <strong>Request recorded (no dispatch)</strong> Alert #${escapeHtml(data.emergency.id)}<br>
        Status: <strong>${escapeHtml(data.dispatchStatus)}</strong> | Helpline: <strong>${escapeHtml(data.emergencyNumber)}</strong><br>
        <small style="margin-top:0.4rem; display:block;">${escapeHtml(data.userInstruction)}</small>
      </div>
    `;
    showToast('Request recorded. Call 112 for immediate help.');
  } catch(err) {
    showToast('Could not record the SOS request. Call 112 for immediate help.');
  }
}
