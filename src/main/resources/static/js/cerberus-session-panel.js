document.addEventListener('alpine:init', function () {
    Alpine.store('app', {
        tab: 'proxy',
        sessions: []
    });

    Alpine.data('sessionPanel', function (session) {
        return {
            uuid: session.uuid,
            port: session.port,
            proxyType: session.proxyType,
            loading: false,
            entries: [],
            nextId: 0,
            filter: '',
            statusFilter: 'all',
            sortBy: 'time',
            selected: null,
            modalOpen: false,
            modalTitle: '',
            modalContent: '',

            init() {
                window.harViewers = window.harViewers || {};
                window.harViewers[this.uuid] = this;
                // Only backfill from getHar for sessions that already existed before this page
                // load (restored via getProxyList): a session just started via startProxy() has
                // no traffic yet, and its mitmdump process may not even have its embedded API up
                // yet, so calling getHar immediately would just fail with "Connection refused".
                if (session.backfill) {
                    this.load();
                }
            },

            load() {
                this.loading = true;
                return getJSON('getHar?uuid=' + this.uuid).then((data) => {
                    const entries = (data.log && data.log.entries) ? data.log.entries : [];
                    this.entries = entries.map((entry, index) => {
                        entry._id = index;
                        return entry;
                    });
                    this.nextId = this.entries.length;
                    this.selected = this.entries.length ? this.entries[0] : null;
                    return data;
                }).finally(() => {
                    this.loading = false;
                });
            },

            addEntry(entry) {
                entry._id = this.nextId++;
                this.entries.push(entry);
            },

            get filteredEntries() {
                const filter = this.filter;
                const statusFilter = this.statusFilter;
                const list = this.entries.filter((entry) => {
                    if (filter && entry.request.url.indexOf(filter) === -1) {
                        return false;
                    }
                    const status = entry.response ? entry.response.status : 0;
                    if (statusFilter === '2xx' && !(status >= 200 && status < 300)) return false;
                    if (statusFilter === '4xx' && !(status >= 400 && status < 500)) return false;
                    if (statusFilter === '5xx' && status < 500) return false;
                    return true;
                });
                const sortBy = this.sortBy;
                list.sort((a, b) => {
                    if (sortBy === 'status') return (b.response ? b.response.status : 0) - (a.response ? a.response.status : 0);
                    if (sortBy === 'size') return (b.response ? b.response.content.size : 0) - (a.response ? a.response.content.size : 0);
                    return b.time - a.time;
                });
                return list;
            },

            select(entry) {
                this.selected = entry;
            },

            statusClass(status) {
                if (!status) {
                    return 'bg-gray-200 text-gray-700';
                }
                if (status < 400) {
                    return 'bg-green-200 text-green-900';
                }
                if (status < 500) {
                    return 'bg-orange-200 text-orange-900';
                }
                return 'bg-red-200 text-red-900';
            },

            highlightJson(obj) {
                if (!obj) {
                    return '';
                }
                const json = JSON.stringify(obj, null, 2)
                    .replace(/&/g, '&amp;')
                    .replace(/</g, '&lt;')
                    .replace(/>/g, '&gt;');
                return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, (match) => {
                    let cls = 'text-sky-300';
                    if (/^"/.test(match)) {
                        cls = /:$/.test(match) ? 'text-purple-300' : 'text-green-300';
                    } else if (/true|false/.test(match)) {
                        cls = 'text-orange-300';
                    } else if (/null/.test(match)) {
                        cls = 'text-gray-400';
                    }
                    return '<span class="' + cls + '">' + match + '</span>';
                });
            },

            openHarModal() {
                // Also refreshes the table: for browsermob sessions (no live websocket stream)
                // this is the only way to populate/refresh it.
                this.load().then((data) => this.showModal('GET /getHar', data));
            },

            openStatsModal() {
                getJSON('getStats?uuid=' + this.uuid).then((data) => this.showModal('GET /getStats', data));
            },

            clearHar() {
                getJSON('clearHar?uuid=' + this.uuid).then((data) => {
                    this.entries = [];
                    this.nextId = 0;
                    this.selected = null;
                    this.showModal('GET /clearHar', data);
                });
            },

            showModal(title, data) {
                this.modalTitle = title;
                this.modalContent = this.highlightJson(data);
                this.modalOpen = true;
            },

            closeModal() {
                this.modalOpen = false;
            },

            stop() {
                stopProxy(this.uuid);
            }
        };
    });

    loadProxyList();
    connect();
    getDoc("my-proxy-controller");
});
