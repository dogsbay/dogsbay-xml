/* DogsBay report kit: base helpers. Plain ES2020, no dependencies. Exposes window.Report. */
(function () {
  'use strict';

  var THEME_KEY = 'dogsbay-report-theme';

  function readJson(id) {
    var node = document.getElementById(id);
    if (!node) return null;
    var text = (node.textContent || '').trim();
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch (e) {
      if (window.console) console.error('Report: cannot parse #' + id, e);
      return null;
    }
  }

  var cache = {};
  function data() {
    if (!('data' in cache)) cache.data = readJson('report-data') || {};
    return cache.data;
  }
  function meta() {
    if (!('meta' in cache)) cache.meta = readJson('report-meta') || {};
    return cache.meta;
  }

  function escapeText(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function append(parent, child) {
    if (child == null || child === false) return;
    if (Array.isArray(child)) {
      child.forEach(function (c) { append(parent, c); });
    } else if (child instanceof Node) {
      parent.appendChild(child);
    } else {
      parent.appendChild(document.createTextNode(String(child)));
    }
  }

  /** Creates an element. attrs: class, text, style (object or string), dataset, on* handlers, others as attributes. */
  function el(tag, attrs) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (key) {
        var value = attrs[key];
        if (value == null || value === false) return;
        if (key === 'class' || key === 'className') node.className = value;
        else if (key === 'text') node.textContent = value;
        else if (key === 'dataset') Object.keys(value).forEach(function (k) { node.dataset[k] = value[k]; });
        else if (key === 'style' && typeof value === 'object') Object.assign(node.style, value);
        else if (key.slice(0, 2) === 'on' && typeof value === 'function') node.addEventListener(key.slice(2).toLowerCase(), value);
        else if (value === true) node.setAttribute(key, '');
        else node.setAttribute(key, String(value));
      });
    }
    for (var i = 2; i < arguments.length; i++) append(node, arguments[i]);
    return node;
  }

  /**
   * Wires ARIA tabs inside rootEl: [role=tab] elements with aria-controls pointing at [role=tabpanel] ids.
   * Returns { select(idOrIndex), current(), onChange(fn) }.
   */
  function tabs(rootEl) {
    var tabList = Array.prototype.slice.call(rootEl.querySelectorAll('[role="tab"]'));
    var listeners = [];
    function panelOf(tab) { return document.getElementById(tab.getAttribute('aria-controls')); }
    function select(which, focus) {
      var target = typeof which === 'number' ? tabList[which]
        : tabList.filter(function (t) { return t.id === which || t.getAttribute('aria-controls') === which; })[0];
      if (!target) return;
      tabList.forEach(function (t) {
        var on = t === target;
        t.setAttribute('aria-selected', on ? 'true' : 'false');
        t.tabIndex = on ? 0 : -1;
        var p = panelOf(t);
        if (p) p.hidden = !on;
      });
      if (focus) target.focus();
      listeners.forEach(function (fn) { fn(target.getAttribute('aria-controls'), target); });
    }
    tabList.forEach(function (tab, index) {
      var p = panelOf(tab);
      if (p) { p.setAttribute('aria-labelledby', tab.id); if (!p.hasAttribute('tabindex')) p.tabIndex = 0; }
      tab.addEventListener('click', function () { select(index); });
      tab.addEventListener('keydown', function (e) {
        var next = null;
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = (index + 1) % tabList.length;
        else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') next = (index - 1 + tabList.length) % tabList.length;
        else if (e.key === 'Home') next = 0;
        else if (e.key === 'End') next = tabList.length - 1;
        if (next !== null) { e.preventDefault(); select(next, true); }
      });
    });
    var initial = tabList.findIndex(function (t) { return t.getAttribute('aria-selected') === 'true'; });
    select(initial < 0 ? 0 : initial);
    return {
      select: function (which) { select(which, false); },
      current: function () {
        var t = tabList.filter(function (x) { return x.getAttribute('aria-selected') === 'true'; })[0];
        return t ? t.getAttribute('aria-controls') : null;
      },
      onChange: function (fn) { listeners.push(fn); }
    };
  }

  function storedTheme() {
    try {
      var v = window.localStorage.getItem(THEME_KEY);
      return v === 'light' || v === 'dark' ? v : null;
    } catch (e) {
      return null;
    }
  }

  /**
   * Makes buttonEl a light/dark toggle. With no stored choice the page follows the system theme and no
   * data-theme attribute is set. Clicking sets an explicit theme and remembers it where storage allows.
   */
  function themeToggle(buttonEl) {
    var root = document.documentElement;
    var media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    function effective() {
      var explicit = root.getAttribute('data-theme');
      if (explicit === 'light' || explicit === 'dark') return explicit;
      return media && media.matches ? 'dark' : 'light';
    }
    function sync() {
      var dark = effective() === 'dark';
      buttonEl.setAttribute('aria-pressed', dark ? 'true' : 'false');
      buttonEl.textContent = dark ? 'Dark theme' : 'Light theme';
      buttonEl.title = 'Switch to ' + (dark ? 'light' : 'dark') + ' theme';
    }
    var stored = storedTheme();
    if (stored) root.setAttribute('data-theme', stored);
    buttonEl.classList.add('theme-toggle');
    buttonEl.addEventListener('click', function () {
      var next = effective() === 'dark' ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try { window.localStorage.setItem(THEME_KEY, next); } catch (e) { /* storage unavailable */ }
      sync();
    });
    if (media) {
      var onChange = function () { sync(); };
      if (media.addEventListener) media.addEventListener('change', onChange);
      else if (media.addListener) media.addListener(onChange);
    }
    sync();
  }

  function cellValue(column, row) {
    if (column.value) return column.value(row);
    return row[column.key];
  }

  function compareValues(a, b) {
    var an = a == null || a === '', bn = b == null || b === '';
    if (an || bn) return an === bn ? 0 : an ? 1 : -1;
    if (typeof a === 'number' && typeof b === 'number') return a - b;
    return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
  }

  /**
   * Renders a table into container.
   * columns: [{ key, label, value?(row), render?(row) -> Node|string, className? }]
   * options: { sortable, filterInput, caption, empty, countEl }
   * Returns { update(rows), rows(), visibleCount() }.
   */
  function table(container, columns, rows, options) {
    var opts = options || {};
    var allRows = (rows || []).slice();
    var sortIndex = -1, sortDir = 1, visible = 0;
    container.textContent = '';

    var wrap = el('div', { class: 'table-wrap' });
    var tableEl = el('table', { class: 'report-table' });
    if (opts.caption) tableEl.appendChild(el('caption', { class: 'visually-hidden', text: opts.caption }));
    var headRow = el('tr');
    var headers = columns.map(function (col, i) {
      var th = el('th', { scope: 'col' });
      if (opts.sortable) {
        th.setAttribute('aria-sort', 'none');
        th.appendChild(el('button', { type: 'button', onclick: function () {
          if (sortIndex === i) sortDir = -sortDir; else { sortIndex = i; sortDir = 1; }
          draw();
        } }, col.label));
      } else {
        th.textContent = col.label;
      }
      headRow.appendChild(th);
      return th;
    });
    tableEl.appendChild(el('thead', null, headRow));
    var body = el('tbody');
    tableEl.appendChild(body);
    wrap.appendChild(tableEl);
    var emptyEl = el('div', { class: 'empty', role: 'status' });
    container.appendChild(wrap);
    container.appendChild(emptyEl);

    function rowText(row) {
      return columns.map(function (c) {
        var v = c.filterValue ? c.filterValue(row) : cellValue(c, row);
        return Array.isArray(v) ? v.join(' ') : v == null ? '' : String(v);
      }).join(' ').toLowerCase();
    }

    function draw() {
      var query = opts.filterInput ? opts.filterInput.value.trim().toLowerCase() : '';
      var list = allRows.filter(function (r) { return !query || rowText(r).indexOf(query) >= 0; });
      if (sortIndex >= 0) {
        var col = columns[sortIndex];
        list.sort(function (a, b) { return sortDir * compareValues(cellValue(col, a), cellValue(col, b)); });
      }
      headers.forEach(function (th, i) {
        if (opts.sortable) th.setAttribute('aria-sort', i !== sortIndex ? 'none' : sortDir > 0 ? 'ascending' : 'descending');
      });
      body.textContent = '';
      var frag = document.createDocumentFragment();
      list.forEach(function (row) {
        var tr = el('tr');
        columns.forEach(function (col) {
          var td = el('td', col.className ? { class: col.className } : null);
          if (col.render) append(td, col.render(row));
          else { var v = cellValue(col, row); td.textContent = v == null ? '' : String(v); }
          tr.appendChild(td);
        });
        frag.appendChild(tr);
      });
      body.appendChild(frag);
      visible = list.length;
      var none = list.length === 0;
      wrap.hidden = none;
      emptyEl.hidden = !none;
      emptyEl.textContent = none ? (allRows.length === 0 ? (opts.empty || 'Nothing to show.') : 'No rows match the filter.') : '';
      if (opts.countEl) {
        opts.countEl.textContent = list.length === allRows.length
          ? allRows.length + ' rows' : list.length + ' of ' + allRows.length + ' rows';
      }
    }

    if (opts.filterInput) opts.filterInput.addEventListener('input', draw);
    draw();
    return {
      update: function (newRows) { allRows = (newRows || []).slice(); draw(); },
      rows: function () { return allRows.slice(); },
      visibleCount: function () { return visible; }
    };
  }

  function badge(label, kind) {
    return el('span', { class: 'badge badge-' + String(kind || label).toLowerCase() }, String(label));
  }

  window.Report = Object.assign(window.Report || {}, {
    data: data,
    meta: meta,
    el: el,
    tabs: tabs,
    themeToggle: themeToggle,
    table: table,
    badge: badge,
    escapeText: escapeText
  });
})();
