/* DogsBay report kit: force-directed SVG graph. Plain ES2020, no dependencies. Requires the base kit. */
(function () {
  'use strict';

  var NS = 'http://www.w3.org/2000/svg';
  var instance = 0;

  function svgEl(tag, attrs) {
    var node = document.createElementNS(NS, tag);
    if (attrs) Object.keys(attrs).forEach(function (k) { if (attrs[k] != null) node.setAttribute(k, String(attrs[k])); });
    return node;
  }

  var SHAPES = {
    square: function () { return svgEl('rect', { x: -9, y: -9, width: 18, height: 18, rx: 2 }); },
    circle: function () { return svgEl('circle', { r: 10 }); },
    diamond: function () { return svgEl('path', { d: 'M0,-12 L12,0 L0,12 L-12,0 Z' }); },
    chevron: function () { return svgEl('path', { d: 'M-11,-9 L2,-9 L11,0 L2,9 L-11,9 L-3,0 Z' }); }
  };

  function truncate(text, max) {
    text = String(text == null ? '' : text);
    return text.length > max ? text.slice(0, max - 1) + '…' : text;
  }

  /**
   * Report.graph(svgEl, { nodes, edges, nodeStyle(node) -> {shape, cls, label}, edgeStyle(edge) -> {dash, cls, label},
   *   onSelect(node), label(node) })
   * nodes need an id; edges need from and to. Returns { relayout(), setVisible(nodePred, edgePred), select(id),
   *   selected(), fit() }.
   */
  function graph(svg, options) {
    var opts = options || {};
    var id = ++instance;
    var nodeStyle = opts.nodeStyle || function () { return {}; };
    var edgeStyle = opts.edgeStyle || function () { return {}; };
    var labelOf = opts.label || function (n) { return n.title || n.id; };

    while (svg.firstChild) svg.removeChild(svg.firstChild);
    svg.classList.add('report-graph');
    svg.setAttribute('role', 'group');
    if (!svg.hasAttribute('aria-label')) svg.setAttribute('aria-label', 'Relationship graph');

    var defs = svgEl('defs');
    var markerId = 'g-arrow-' + id;
    var marker = svgEl('marker', { id: markerId, viewBox: '0 0 10 10', refX: 10, refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto-start-reverse' });
    marker.appendChild(svgEl('path', { d: 'M0,0 L10,5 L0,10 Z', class: 'g-arrow' }));
    defs.appendChild(marker);
    svg.appendChild(defs);
    var edgeLayer = svgEl('g', { class: 'g-edges' });
    var nodeLayer = svgEl('g', { class: 'g-nodes' });
    svg.appendChild(edgeLayer);
    svg.appendChild(nodeLayer);

    var nodes = [];
    var byId = new Map();
    (opts.nodes || []).forEach(function (data, i) {
      if (data == null || data.id == null || byId.has(data.id)) return;
      var style = nodeStyle(data) || {};
      var angle = i * 2.399963;
      var radius = 30 * Math.sqrt(i + 1);
      var n = { data: data, x: Math.cos(angle) * radius, y: Math.sin(angle) * radius, vx: 0, vy: 0, fixed: false, visible: true, degree: 0 };
      var g = svgEl('g', { class: 'g-node' + (style.cls ? ' ' + style.cls : ''), tabindex: 0, role: 'button', 'aria-pressed': 'false' });
      var label = labelOf(data) + (style.label ? ' (' + style.label + ')' : '');
      g.setAttribute('aria-label', label);
      var title = svgEl('title');
      title.textContent = label + (data.id !== labelOf(data) ? ' — ' + data.id : '');
      g.appendChild(title);
      var shape = (SHAPES[style.shape] || SHAPES.circle)();
      shape.setAttribute('class', 'g-shape');
      g.appendChild(shape);
      var text = svgEl('text', { class: 'g-label', y: 24 });
      text.textContent = truncate(labelOf(data), 28) + (style.label ? ' [' + style.label + ']' : '');
      g.appendChild(text);
      n.el = g;
      nodeLayer.appendChild(g);
      nodes.push(n);
      byId.set(data.id, n);
    });

    var edges = [];
    (opts.edges || []).forEach(function (data) {
      var a = byId.get(data.from), b = byId.get(data.to);
      if (!a || !b || a === b) return;
      var style = edgeStyle(data) || {};
      var line = svgEl('line', { class: 'g-edge' + (style.cls ? ' ' + style.cls : ''), 'marker-end': 'url(#' + markerId + ')' });
      if (style.dash) line.setAttribute('stroke-dasharray', style.dash);
      var t = svgEl('title');
      t.textContent = (data.kind || 'edge') + ': ' + labelOf(a.data) + ' → ' + labelOf(b.data) + (style.label ? ' (' + style.label + ')' : '');
      line.appendChild(t);
      edgeLayer.appendChild(line);
      var labelEl = null;
      if (style.label) {
        labelEl = svgEl('text', { class: 'g-edge-label' });
        labelEl.textContent = style.label;
        edgeLayer.appendChild(labelEl);
      }
      a.degree++; b.degree++;
      edges.push({ data: data, a: a, b: b, el: line, labelEl: labelEl, visible: true });
    });

    var selectedNode = null;
    var frame = 0;

    function draw() {
      nodes.forEach(function (n) {
        if (n.visible) n.el.setAttribute('transform', 'translate(' + n.x.toFixed(1) + ',' + n.y.toFixed(1) + ')');
      });
      edges.forEach(function (e) {
        if (!e.visible) return;
        var dx = e.b.x - e.a.x, dy = e.b.y - e.a.y;
        var d = Math.sqrt(dx * dx + dy * dy) || 1;
        var pad = 14;
        e.el.setAttribute('x1', (e.a.x + dx / d * pad).toFixed(1));
        e.el.setAttribute('y1', (e.a.y + dy / d * pad).toFixed(1));
        e.el.setAttribute('x2', (e.b.x - dx / d * pad).toFixed(1));
        e.el.setAttribute('y2', (e.b.y - dy / d * pad).toFixed(1));
        if (e.labelEl) {
          e.labelEl.setAttribute('x', ((e.a.x + e.b.x) / 2).toFixed(1));
          e.labelEl.setAttribute('y', ((e.a.y + e.b.y) / 2 - 4).toFixed(1));
        }
      });
    }

    function fit() {
      var vis = nodes.filter(function (n) { return n.visible; });
      if (!vis.length) { svg.setAttribute('viewBox', '-200 -150 400 300'); return; }
      var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      vis.forEach(function (n) {
        minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
        minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y);
      });
      var margin = 60;
      var w = Math.max(maxX - minX + margin * 2, 300), h = Math.max(maxY - minY + margin * 2, 220);
      var cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
      svg.setAttribute('viewBox', [(cx - w / 2).toFixed(1), (cy - h / 2).toFixed(1), w.toFixed(1), h.toFixed(1)].join(' '));
    }

    /** One simulation step over visible nodes. Returns the largest displacement. */
    function step(active, activeEdges, temperature) {
      var count = active.length;
      var k = 70; // ideal edge length
      var i, j, a, b, dx, dy, d2, d, f;
      for (i = 0; i < count; i++) { active[i].fx = 0; active[i].fy = 0; }
      // Repulsion (all pairs; the caller caps total work).
      for (i = 0; i < count; i++) {
        a = active[i];
        for (j = i + 1; j < count; j++) {
          b = active[j];
          dx = a.x - b.x; dy = a.y - b.y;
          d2 = dx * dx + dy * dy;
          if (d2 < 0.01) { dx = (Math.random() - 0.5); dy = (Math.random() - 0.5); d2 = 0.5; }
          if (d2 > 250000) continue; // ignore pairs further than 500 units
          f = (k * k) / d2;
          a.fx += dx * f; a.fy += dy * f;
          b.fx -= dx * f; b.fy -= dy * f;
        }
      }
      // Springs.
      activeEdges.forEach(function (e) {
        dx = e.b.x - e.a.x; dy = e.b.y - e.a.y;
        d = Math.sqrt(dx * dx + dy * dy) || 0.1;
        f = (d - k) * 0.06 / d;
        e.a.fx += dx * f; e.a.fy += dy * f;
        e.b.fx -= dx * f; e.b.fy -= dy * f;
      });
      var maxMove = 0;
      for (i = 0; i < count; i++) {
        a = active[i];
        // Gravity toward the origin keeps disconnected parts together.
        a.fx -= a.x * 0.012; a.fy -= a.y * 0.012;
        if (a.fixed) { a.vx = 0; a.vy = 0; continue; }
        a.vx = (a.vx + a.fx) * 0.82; // damping
        a.vy = (a.vy + a.fy) * 0.82;
        var speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy);
        if (speed > temperature) { a.vx = a.vx / speed * temperature; a.vy = a.vy / speed * temperature; speed = temperature; }
        a.x += a.vx; a.y += a.vy;
        if (speed > maxMove) maxMove = speed;
      }
      return maxMove;
    }

    function relayout() {
      if (frame) cancelAnimationFrame(frame);
      var active = nodes.filter(function (n) { return n.visible; });
      var activeEdges = edges.filter(function (e) { return e.visible; });
      var n = Math.max(active.length, 1);
      // Budget about 12 million pair interactions in total, between 40 and 400 iterations.
      var iterations = Math.max(40, Math.min(400, Math.floor(12e6 / (n * n))));
      var perFrame = Math.max(1, Math.floor(400000 / (n * n)));
      var done = 0;
      var temperature = 40;
      var cooling = Math.pow(0.5 / temperature, 1 / iterations);
      var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      function tick() {
        var batch = reduced ? iterations : perFrame;
        var moved = 0;
        for (var s = 0; s < batch && done < iterations; s++, done++) {
          moved = step(active, activeEdges, temperature);
          temperature *= cooling;
        }
        fit();
        draw();
        if (done < iterations && moved > 0.05) frame = requestAnimationFrame(tick);
        else frame = 0;
      }
      tick();
    }

    function select(nodeId, notify) {
      var n = nodeId == null ? null : byId.get(nodeId) || null;
      if (selectedNode) { selectedNode.el.classList.remove('is-selected'); selectedNode.el.setAttribute('aria-pressed', 'false'); }
      selectedNode = n;
      if (n) { n.el.classList.add('is-selected'); n.el.setAttribute('aria-pressed', 'true'); }
      if (notify !== false && opts.onSelect) opts.onSelect(n ? n.data : null);
      return !!n;
    }

    function svgPoint(evt) {
      var ctm = svg.getScreenCTM();
      if (!ctm) return { x: 0, y: 0 };
      var inv = ctm.inverse();
      return { x: evt.clientX * inv.a + evt.clientY * inv.c + inv.e, y: evt.clientX * inv.b + evt.clientY * inv.d + inv.f };
    }

    nodes.forEach(function (n) {
      var drag = null;
      n.el.addEventListener('pointerdown', function (evt) {
        if (evt.button !== 0) return;
        var p = svgPoint(evt);
        drag = { dx: n.x - p.x, dy: n.y - p.y, sx: evt.clientX, sy: evt.clientY, moved: false };
        n.fixed = true;
        try { n.el.setPointerCapture(evt.pointerId); } catch (e) { /* ignore */ }
        evt.preventDefault();
      });
      n.el.addEventListener('pointermove', function (evt) {
        if (!drag) return;
        if (Math.abs(evt.clientX - drag.sx) + Math.abs(evt.clientY - drag.sy) > 3) drag.moved = true;
        if (!drag.moved) return;
        var p = svgPoint(evt);
        n.x = p.x + drag.dx; n.y = p.y + drag.dy;
        draw();
      });
      function end(evt) {
        if (!drag) return;
        var wasClick = !drag.moved;
        drag = null;
        n.fixed = false;
        try { n.el.releasePointerCapture(evt.pointerId); } catch (e) { /* ignore */ }
        if (wasClick) { select(n.data.id); n.el.focus(); }
      }
      n.el.addEventListener('pointerup', end);
      n.el.addEventListener('pointercancel', function () { drag = null; n.fixed = false; });
      n.el.addEventListener('keydown', function (evt) {
        if (evt.key === 'Enter' || evt.key === ' ') { evt.preventDefault(); select(n.data.id); }
      });
    });

    function setVisible(nodePred, edgePred) {
      nodes.forEach(function (n) {
        n.visible = !nodePred || !!nodePred(n.data);
        n.el.classList.toggle('is-hidden', !n.visible);
        n.el.setAttribute('tabindex', n.visible ? '0' : '-1');
      });
      edges.forEach(function (e) {
        e.visible = e.a.visible && e.b.visible && (!edgePred || !!edgePred(e.data));
        e.el.classList.toggle('is-hidden', !e.visible);
        if (e.labelEl) e.labelEl.classList.toggle('is-hidden', !e.visible);
      });
      if (selectedNode && !selectedNode.visible) select(null);
      fit();
      draw();
    }

    fit();
    relayout();

    return {
      relayout: relayout,
      setVisible: setVisible,
      select: function (nodeId) {
        var ok = select(nodeId);
        if (ok) { var n = byId.get(nodeId); if (n.visible) n.el.focus({ preventScroll: true }); }
        return ok;
      },
      selected: function () { return selectedNode ? selectedNode.data : null; },
      has: function (nodeId) { return byId.has(nodeId); },
      isVisible: function (nodeId) { var n = byId.get(nodeId); return !!(n && n.visible); },
      fit: function () { fit(); draw(); }
    };
  }

  window.Report = window.Report || {};
  window.Report.graph = graph;
})();
