/* TS3-DNS Cluster Admin UI – app.js */
'use strict';

// ============================================================
// Auth check
// ============================================================
(async function checkAuth() {
    try {
        const r = await fetch('/api/me');
        if (!r.ok) {
            window.location.href = '/login.html';
        } else {
            const data = await r.json();
            document.getElementById('versionLabel').textContent = 'Logged in as: ' + (data.username || '');
        }
    } catch (e) {
        window.location.href = '/login.html';
    }
})();

// ============================================================
// Navigation
// ============================================================
const pageTitles = {
    dashboard: 'Dashboard',
    dns:       'DNS Entries',
    servers:   'TS3 Servers',
    stats:     'Statistics',
    cluster:   'Cluster'
};

function showSection(name) {
    document.querySelectorAll('.page-section').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('.nav-link[data-section]').forEach(l => l.classList.remove('active'));

    const section = document.getElementById('section-' + name);
    if (section) section.classList.add('active');

    const link = document.querySelector('.nav-link[data-section="' + name + '"]');
    if (link) link.classList.add('active');

    document.getElementById('currentPageTitle').textContent = pageTitles[name] || name;

    // Load data for section
    switch (name) {
        case 'dashboard': loadDashboard(); break;
        case 'dns':       loadDns(); break;
        case 'servers':   loadServers(); break;
        case 'stats':     loadStats(); break;
        case 'cluster':   loadCluster(); break;
    }

    // Close sidebar on mobile
    document.getElementById('sidebar').classList.remove('open');
}

document.querySelectorAll('.nav-link[data-section]').forEach(link => {
    link.addEventListener('click', e => {
        e.preventDefault();
        showSection(link.dataset.section);
    });
});

document.getElementById('sidebarToggle').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('open');
});

document.getElementById('logoutBtn').addEventListener('click', async (e) => {
    e.preventDefault();
    await fetch('/api/logout', { method: 'POST' });
    window.location.href = '/login.html';
});

// ============================================================
// Toast notifications
// ============================================================
function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    const id = 'toast-' + Date.now();
    const bgClass = type === 'success' ? 'bg-success' : 'bg-danger';
    const html = `
        <div id="${id}" class="toast align-items-center text-white ${bgClass} border-0 mb-2" role="alert" aria-live="assertive">
            <div class="d-flex">
                <div class="toast-body">${escapeHtml(message)}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>`;
    container.insertAdjacentHTML('beforeend', html);
    const toastEl = document.getElementById(id);
    const toast = new bootstrap.Toast(toastEl, { delay: 3500 });
    toast.show();
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
}

// ============================================================
// Helpers
// ============================================================
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function fmtTimestamp(ts) {
    if (!ts || ts === 0) return '—';
    const d = new Date(ts * 1000);
    return d.toLocaleString();
}

async function apiFetch(url, options) {
    const resp = await fetch(url, options);
    if (resp.status === 401) {
        window.location.href = '/login.html';
        return null;
    }
    return resp;
}

// ============================================================
// Dashboard
// ============================================================
async function loadDashboard() {
    const r = await apiFetch('/api/stats');
    if (!r) return;
    const data = await r.json();
    if (r.ok) {
        document.getElementById('stat-total-dns').textContent      = data.total_dns ?? '—';
        document.getElementById('stat-total-queries').textContent  = data.total_queries ?? '—';
        document.getElementById('stat-total-servers').textContent  = data.total_servers ?? '—';
        document.getElementById('stat-online-servers').textContent = data.online_servers ?? '—';

        const topBody = document.getElementById('topDomainsTable');
        topBody.innerHTML = '';
        (data.top_domains || []).forEach((d, i) => {
            topBody.insertAdjacentHTML('beforeend',
                `<tr><td>${escapeHtml(d.dns)}</td><td>${escapeHtml(d.ip)}:${escapeHtml(d.port)}</td><td>${d.usecount}</td></tr>`);
        });
        if (!data.top_domains || data.top_domains.length === 0) {
            topBody.innerHTML = '<tr><td colspan="3" class="text-muted text-center">No data</td></tr>';
        }

        const recBody = document.getElementById('recentDomainsTable');
        recBody.innerHTML = '';
        (data.recent_domains || []).forEach(d => {
            recBody.insertAdjacentHTML('beforeend',
                `<tr><td>${escapeHtml(d.dns)}</td><td>${escapeHtml(d.ip)}:${escapeHtml(d.port)}</td><td>${fmtTimestamp(d.lastused)}</td></tr>`);
        });
        if (!data.recent_domains || data.recent_domains.length === 0) {
            recBody.innerHTML = '<tr><td colspan="3" class="text-muted text-center">No data</td></tr>';
        }
    }
}

// ============================================================
// DNS Management
// ============================================================
let dnsData = [];

async function loadDns() {
    const r = await apiFetch('/api/dns');
    if (!r) return;
    dnsData = await r.json();
    renderDnsTable();
}

function renderDnsTable() {
    const tbody = document.getElementById('dnsTableBody');
    tbody.innerHTML = '';
    if (!dnsData.length) {
        tbody.innerHTML = '<tr><td colspan="10" class="text-muted text-center py-3">No DNS entries found.</td></tr>';
        return;
    }
    dnsData.forEach(d => {
        const failback = d.failback ? '<span class="badge bg-success">Yes</span>' : '<span class="badge bg-secondary">No</span>';
        const isDefault = d.is_default ? '<span class="badge bg-primary">Yes</span>' : '';
        tbody.insertAdjacentHTML('beforeend', `
            <tr>
                <td>${d.id}</td>
                <td><strong>${escapeHtml(d.dns)}</strong></td>
                <td>${escapeHtml(d.ip)}</td>
                <td>${escapeHtml(d.port)}</td>
                <td>${failback}</td>
                <td>${isDefault}</td>
                <td>${d.machine_id || 0}</td>
                <td>${d.usecount || 0}</td>
                <td>${d.active_slots || 0}/${d.slots || 0}</td>
                <td>
                    <button class="btn btn-outline-light btn-sm py-0 px-1" onclick="editDns(${d.id})" title="Edit">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-outline-danger btn-sm py-0 px-1 ms-1" onclick="deleteDns(${d.id}, '${escapeHtml(d.dns)}')" title="Delete">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>`);
    });
}

const dnsModal = new bootstrap.Modal(document.getElementById('dnsModal'));

function openDnsModal(id) {
    document.getElementById('dnsEditId').value = '';
    document.getElementById('dnsModalTitle').textContent = 'Add DNS Entry';
    document.getElementById('dnsDns').value = '';
    document.getElementById('dnsIp').value = '';
    document.getElementById('dnsPort').value = '9987';
    document.getElementById('dnsFailbackIp').value = '';
    document.getElementById('dnsFailbackPort').value = '9987';
    document.getElementById('dnsServerId').value = '0';
    document.getElementById('dnsMachineId').value = '0';
    document.getElementById('dnsFailback').checked = false;
    document.getElementById('dnsDefault').checked = false;
    dnsModal.show();
}

function editDns(id) {
    const d = dnsData.find(x => x.id === id);
    if (!d) return;
    document.getElementById('dnsEditId').value = id;
    document.getElementById('dnsModalTitle').textContent = 'Edit DNS Entry';
    document.getElementById('dnsDns').value = d.dns || '';
    document.getElementById('dnsIp').value = d.ip || '';
    document.getElementById('dnsPort').value = d.port || '9987';
    document.getElementById('dnsFailbackIp').value = d.failback_ip || '';
    document.getElementById('dnsFailbackPort').value = d.failback_port || '9987';
    document.getElementById('dnsServerId').value = d.server_id || '0';
    document.getElementById('dnsMachineId').value = d.machine_id || '0';
    document.getElementById('dnsFailback').checked = !!d.failback;
    document.getElementById('dnsDefault').checked = !!d.is_default;
    dnsModal.show();
}

async function saveDns() {
    const id = document.getElementById('dnsEditId').value;
    const payload = {
        dns:          document.getElementById('dnsDns').value.trim(),
        ip:           document.getElementById('dnsIp').value.trim(),
        port:         document.getElementById('dnsPort').value.trim(),
        failback_ip:  document.getElementById('dnsFailbackIp').value.trim(),
        failback_port:document.getElementById('dnsFailbackPort').value.trim(),
        server_id:    document.getElementById('dnsServerId').value.trim(),
        machine_id:   document.getElementById('dnsMachineId').value.trim(),
        failback:     String(document.getElementById('dnsFailback').checked),
        is_default:   String(document.getElementById('dnsDefault').checked)
    };
    if (!payload.dns || !payload.ip) {
        showToast('DNS name and IP are required.', 'error');
        return;
    }
    const url    = id ? '/api/dns/' + id : '/api/dns';
    const method = id ? 'PUT' : 'POST';
    const r = await apiFetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    if (!r) return;
    const data = await r.json();
    if (r.ok) {
        dnsModal.hide();
        showToast(id ? 'DNS entry updated.' : 'DNS entry created.');
        loadDns();
    } else {
        showToast(data.error || 'Error saving DNS entry.', 'error');
    }
}

async function deleteDns(id, name) {
    if (!confirm('Delete DNS entry "' + name + '"?')) return;
    const r = await apiFetch('/api/dns/' + id, { method: 'DELETE' });
    if (!r) return;
    const data = await r.json();
    if (r.ok) { showToast('DNS entry deleted.'); loadDns(); }
    else showToast(data.error || 'Error deleting DNS entry.', 'error');
}

// ============================================================
// Servers Management
// ============================================================
let serversData = [];

async function loadServers() {
    const r = await apiFetch('/api/servers');
    if (!r) return;
    serversData = await r.json();
    renderServersTable();
}

function renderServersTable() {
    const tbody = document.getElementById('serversTableBody');
    tbody.innerHTML = '';
    if (!serversData.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">No servers found.</td></tr>';
        return;
    }
    serversData.forEach(s => {
        const statusBadge = s.online
            ? '<span class="badge badge-online">Online</span>'
            : '<span class="badge badge-offline">Offline</span>';
        tbody.insertAdjacentHTML('beforeend', `
            <tr>
                <td>${s.id}</td>
                <td>${escapeHtml(s.ip)}</td>
                <td>${escapeHtml(s.port)}</td>
                <td>${escapeHtml(s.username)}</td>
                <td>${statusBadge}</td>
                <td>
                    <button class="btn btn-outline-light btn-sm py-0 px-1" onclick="editServer(${s.id})" title="Edit">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-outline-danger btn-sm py-0 px-1 ms-1" onclick="deleteServer(${s.id}, '${escapeHtml(s.ip)}')" title="Delete">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>`);
    });
}

const serverModal = new bootstrap.Modal(document.getElementById('serverModal'));

function openServerModal() {
    document.getElementById('serverEditId').value = '';
    document.getElementById('serverModalTitle').textContent = 'Add TS3 Server';
    document.getElementById('serverIp').value = '';
    document.getElementById('serverPort').value = '10011';
    document.getElementById('serverUsername').value = '';
    document.getElementById('serverPassword').value = '';
    document.getElementById('serverPasswordHint').style.display = 'none';
    serverModal.show();
}

function editServer(id) {
    const s = serversData.find(x => x.id === id);
    if (!s) return;
    document.getElementById('serverEditId').value = id;
    document.getElementById('serverModalTitle').textContent = 'Edit TS3 Server';
    document.getElementById('serverIp').value = s.ip || '';
    document.getElementById('serverPort').value = s.port || '10011';
    document.getElementById('serverUsername').value = s.username || '';
    document.getElementById('serverPassword').value = '';
    document.getElementById('serverPasswordHint').style.display = 'block';
    serverModal.show();
}

async function saveServer() {
    const id = document.getElementById('serverEditId').value;
    const payload = {
        ip:       document.getElementById('serverIp').value.trim(),
        port:     document.getElementById('serverPort').value.trim(),
        username: document.getElementById('serverUsername').value.trim(),
        password: document.getElementById('serverPassword').value
    };
    if (!payload.ip || !payload.port || !payload.username) {
        showToast('IP, port and username are required.', 'error');
        return;
    }
    const url    = id ? '/api/servers/' + id : '/api/servers';
    const method = id ? 'PUT' : 'POST';
    const r = await apiFetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    if (!r) return;
    const data = await r.json();
    if (r.ok) {
        serverModal.hide();
        showToast(id ? 'Server updated.' : 'Server added.');
        loadServers();
    } else {
        showToast(data.error || 'Error saving server.', 'error');
    }
}

async function deleteServer(id, ip) {
    if (!confirm('Remove server ' + ip + '?')) return;
    const r = await apiFetch('/api/servers/' + id, { method: 'DELETE' });
    if (!r) return;
    const data = await r.json();
    if (r.ok) { showToast('Server removed.'); loadServers(); }
    else showToast(data.error || 'Error removing server.', 'error');
}

// ============================================================
// Statistics
// ============================================================
async function loadStats() {
    const r = await apiFetch('/api/stats');
    if (!r) return;
    const data = await r.json();
    const tbody = document.getElementById('statsTableBody');
    tbody.innerHTML = '';
    const domains = data.top_domains || [];
    if (!domains.length) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-muted text-center py-3">No statistics available.</td></tr>';
        return;
    }
    domains.forEach((d, i) => {
        const slotsBar = d.slots > 0
            ? `<div class="progress" style="height:6px;min-width:60px">
                 <div class="progress-bar bg-success" style="width:${Math.round(d.active_slots/d.slots*100)}%"></div>
               </div>`
            : '—';
        tbody.insertAdjacentHTML('beforeend', `
            <tr>
                <td>${i+1}</td>
                <td><strong>${escapeHtml(d.dns)}</strong></td>
                <td>${escapeHtml(d.ip)}:${escapeHtml(d.port)}</td>
                <td>${d.usecount}</td>
                <td>${d.active_slots}</td>
                <td>${d.slots} ${slotsBar}</td>
                <td>${fmtTimestamp(d.lastused)}</td>
            </tr>`);
    });
}

// ============================================================
// Cluster
// ============================================================
async function loadCluster() {
    const r = await apiFetch('/api/cluster');
    if (!r) return;
    const d = await r.json();
    const container = document.getElementById('clusterInfo');

    let roleHtml;
    if (d.is_master) {
        roleHtml = '<span class="cluster-badge cluster-master"><i class="bi bi-crown-fill"></i> Master</span>';
    } else if (d.is_slave) {
        roleHtml = '<span class="cluster-badge cluster-slave"><i class="bi bi-arrow-down-circle-fill"></i> Slave</span>';
    } else {
        roleHtml = '<span class="cluster-badge cluster-standalone"><i class="bi bi-circle"></i> Standalone</span>';
    }

    const cbHtml = d.couchbase && d.couchbase.enabled
        ? `<span class="badge bg-success">Enabled</span> Host: <code>${escapeHtml(d.couchbase.host)}</code> / Bucket: <code>${escapeHtml(d.couchbase.bucket)}</code>`
        : '<span class="badge bg-secondary">Disabled</span>';

    container.innerHTML = `
        <div class="col-md-6">
            <div class="stat-card">
                <h6 class="section-title">Node Information</h6>
                <table class="table table-sm mb-0">
                    <tr><th>Version</th><td>${escapeHtml(d.version)}</td></tr>
                    <tr><th>Machine ID</th><td>${d.machine_id}</td></tr>
                    <tr><th>DNS Port</th><td>${d.dns_port}</td></tr>
                    <tr><th>Admin UI Port</th><td>${d.admin_ui && d.admin_ui.port}</td></tr>
                    <tr><th>Role</th><td>${roleHtml}</td></tr>
                </table>
            </div>
        </div>
        <div class="col-md-6">
            <div class="stat-card">
                <h6 class="section-title">Couchbase Cluster</h6>
                <p class="mb-2">${cbHtml}</p>
                ${d.couchbase && d.couchbase.enabled
                    ? `<table class="table table-sm mb-0">
                         <tr><th>CB Machine ID</th><td>${d.couchbase.machine_id}</td></tr>
                       </table>` : ''}
            </div>
        </div>
        <div class="col-12">
            <div class="stat-card">
                <h6 class="section-title">Configuration Summary</h6>
                <table class="table table-sm mb-0">
                    <tr><th>Default IP</th><td>${escapeHtml((d.config||{}).default_ip)}</td></tr>
                    <tr><th>Default Port</th><td>${escapeHtml((d.config||{}).default_port)}</td></tr>
                    <tr><th>Debug Mode</th><td>${(d.config||{}).debug ? '<span class="badge bg-warning text-dark">ON</span>' : '<span class="badge bg-secondary">OFF</span>'}</td></tr>
                    <tr><th>Send TS3 Messages</th><td>${(d.config||{}).send_messages ? '<span class="badge bg-success">ON</span>' : '<span class="badge bg-secondary">OFF</span>'}</td></tr>
                </table>
            </div>
        </div>`;
}

// ============================================================
// Bootstrap: load dashboard on start
// ============================================================
loadDashboard();
