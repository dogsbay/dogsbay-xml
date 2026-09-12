<?xml version="1.0" encoding="UTF-8"?>
<!--
  Copyright (C) 2002-2026 DogsBay Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

  SPDX-License-Identifier: Apache-2.0

  dita-preview.xsl : best-effort, single-document DITA -> HTML preview.

  This is NOT a DITA-OT replacement. It renders one topic (or one map as a
  TOC) quickly for the editor's live preview pane: no key resolution, no
  conref transclusion (conrefs render as visible badges), no filtering.
  Unknown / specialized elements pass their children through transparently
  so content is never silently dropped.

  Profiling attributes (audience/platform/product/otherprops) are copied
  onto the output as data-* attributes so a future DITAVAL-filtered preview
  can act on them with CSS alone.
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:dbx="urn:dogsbay:dita-preview"
                exclude-result-prefixes="#all">

  <xsl:output method="html" indent="no"/>

  <!-- file: URL of the directory containing the document; used to resolve
       relative image hrefs. Empty when the document has never been saved. -->
  <xsl:param name="base-uri" select="''"/>

  <!-- Key space of the context map, as <keys><key name href text linktext/></keys>.
       Absent (empty sequence) when no context map is configured — key
       references then render as unresolved placeholders. -->
  <xsl:param name="keyspace" as="document-node()?" select="()"/>

  <!-- Review proposals: false renders the document as if every proposal
       were accepted (the converter strips the marks first); true keeps them
       and the templates below draw them. An element is a proposal when its
       rev carries a review author id (ai:, user:, mcp:, rpc:). -->
  <xsl:param name="show-changes" as="xs:boolean" select="false()"/>

  <!-- The key-space entry for a key reference value ("keyname" or
       "keyname/elemid"), or empty. -->
  <xsl:function name="dbx:keydef" as="element(key)?">
    <xsl:param name="ref" as="xs:string?"/>
    <xsl:variable name="name"
                  select="if (contains($ref, '/')) then substring-before($ref, '/') else $ref"/>
    <xsl:sequence select="$keyspace/keys/key[@name = $name][1]"/>
  </xsl:function>

  <!-- ======================================================================
       Root dispatch
       ====================================================================== -->

  <xsl:template match="/">
    <div class="dita-preview">
      <xsl:apply-templates/>
    </div>
  </xsl:template>

  <!-- Topic-like roots: concept, task, reference, topic, troubleshooting,
       glossentry, and (via the generic rule below) any specialization that
       keeps the title/body shape. -->

  <!-- ======================================================================
       Titles
       ====================================================================== -->

  <!-- Root topic title -->
  <xsl:template match="/*/title">
    <h1><xsl:apply-templates/></h1>
  </xsl:template>

  <!-- Nested topic titles (ditabase / nested topics) -->
  <xsl:template match="topic/title | concept/title | task/title | reference/title | troubleshooting/title | glossentry/title" priority="0.6">
    <h2><xsl:apply-templates/></h2>
  </xsl:template>
  <!-- Root wins over the nested rule -->
  <xsl:template match="/topic/title | /concept/title | /task/title | /reference/title | /troubleshooting/title | /glossentry/title" priority="0.8">
    <h1><xsl:apply-templates/></h1>
  </xsl:template>

  <xsl:template match="section/title | example/title | refsyn/title">
    <h2 class="section-title"><xsl:apply-templates/></h2>
  </xsl:template>

  <xsl:template match="fig/title">
    <p class="fig-title"><xsl:apply-templates/></p>
  </xsl:template>

  <xsl:template match="table/title | simpletable/title">
    <p class="table-title"><xsl:apply-templates/></p>
  </xsl:template>

  <xsl:template match="shortdesc | abstract">
    <p class="shortdesc"><xsl:apply-templates/></p>
  </xsl:template>

  <!-- Prolog metadata is not content -->
  <xsl:template match="prolog | titlealts"/>

  <!-- ======================================================================
       Body / sections
       ====================================================================== -->

  <xsl:template match="body | conbody | taskbody | refbody | troublebody | glossBody">
    <div class="body"><xsl:apply-templates/></div>
  </xsl:template>

  <xsl:template match="section | example | refsyn">
    <div class="section"><xsl:apply-templates/></div>
  </xsl:template>

  <!-- Task-specific labeled blocks -->
  <xsl:template match="prereq">
    <div class="task-block prereq"><p class="task-label">Before you begin</p><xsl:apply-templates/></div>
  </xsl:template>
  <xsl:template match="context">
    <div class="task-block context"><xsl:apply-templates/></div>
  </xsl:template>
  <xsl:template match="result">
    <div class="task-block result"><p class="task-label">Result</p><xsl:apply-templates/></div>
  </xsl:template>
  <xsl:template match="postreq">
    <div class="task-block postreq"><p class="task-label">What to do next</p><xsl:apply-templates/></div>
  </xsl:template>

  <!-- ======================================================================
       Steps
       ====================================================================== -->

  <xsl:template match="steps | steps-unordered | substeps">
    <ol class="steps"><xsl:apply-templates select="step | substep"/></ol>
  </xsl:template>

  <xsl:template match="step | substep">
    <li class="step"><xsl:apply-templates/></li>
  </xsl:template>

  <xsl:template match="cmd">
    <span class="cmd"><xsl:apply-templates/></span>
  </xsl:template>

  <xsl:template match="info | stepresult | stepxmp">
    <div class="step-info"><xsl:apply-templates/></div>
  </xsl:template>

  <xsl:template match="choices">
    <ul class="choices"><xsl:apply-templates/></ul>
  </xsl:template>
  <xsl:template match="choice">
    <li><xsl:apply-templates/></li>
  </xsl:template>

  <!-- ======================================================================
       Notes
       ====================================================================== -->

  <xsl:template match="note">
    <xsl:variable name="type" select="if (@type and @type != '') then @type else 'note'"/>
    <div class="note note-{$type}">
      <p class="note-label">
        <xsl:choose>
          <xsl:when test="$type = 'tip'">Tip</xsl:when>
          <xsl:when test="$type = 'important'">Important</xsl:when>
          <xsl:when test="$type = 'warning'">Warning</xsl:when>
          <xsl:when test="$type = 'caution'">Caution</xsl:when>
          <xsl:when test="$type = 'danger'">Danger</xsl:when>
          <xsl:when test="$type = 'attention'">Attention</xsl:when>
          <xsl:when test="$type = 'remember'">Remember</xsl:when>
          <xsl:when test="$type = 'restriction'">Restriction</xsl:when>
          <xsl:when test="$type = 'trouble'">Trouble</xsl:when>
          <xsl:otherwise>Note</xsl:otherwise>
        </xsl:choose>
      </p>
      <xsl:apply-templates/>
    </div>
  </xsl:template>

  <xsl:template match="hazardstatement">
    <div class="note note-danger"><p class="note-label">Hazard</p><xsl:apply-templates/></div>
  </xsl:template>

  <!-- ======================================================================
       Paragraphs, lists, definition lists
       ====================================================================== -->

  <xsl:template match="p">
    <p><xsl:call-template name="profiling-attrs"/><xsl:apply-templates/></p>
  </xsl:template>

  <xsl:template match="ul">
    <ul><xsl:apply-templates/></ul>
  </xsl:template>
  <xsl:template match="ol">
    <ol><xsl:apply-templates/></ol>
  </xsl:template>
  <xsl:template match="li | sli">
    <li><xsl:call-template name="profiling-attrs"/><xsl:apply-templates/></li>
  </xsl:template>
  <xsl:template match="sl">
    <ul class="simple-list"><xsl:apply-templates/></ul>
  </xsl:template>

  <xsl:template match="dl">
    <dl><xsl:apply-templates/></dl>
  </xsl:template>
  <xsl:template match="dlentry">
    <xsl:apply-templates/>
  </xsl:template>
  <xsl:template match="dt">
    <dt><xsl:apply-templates/></dt>
  </xsl:template>
  <xsl:template match="dd">
    <dd><xsl:apply-templates/></dd>
  </xsl:template>

  <xsl:template match="lq">
    <blockquote><xsl:apply-templates/></blockquote>
  </xsl:template>

  <xsl:template match="fig">
    <figure class="fig"><xsl:apply-templates/></figure>
  </xsl:template>

  <!-- ======================================================================
       Code and message blocks
       ====================================================================== -->

  <xsl:template match="codeblock | msgblock | screen | pre | lines">
    <pre class="codeblock"><code><xsl:apply-templates/></code></pre>
  </xsl:template>

  <xsl:template match="codeph | msgph | synph">
    <code><xsl:apply-templates/></code>
  </xsl:template>

  <!-- ======================================================================
       Inline markup
       ====================================================================== -->

  <xsl:template match="uicontrol | wintitle">
    <span class="uicontrol"><xsl:apply-templates/></span>
  </xsl:template>

  <xsl:template match="menucascade">
    <span class="menucascade">
      <xsl:for-each select="uicontrol">
        <xsl:if test="position() &gt; 1"><span class="menu-sep"> &#8250; </span></xsl:if>
        <span class="uicontrol"><xsl:apply-templates/></span>
      </xsl:for-each>
    </span>
  </xsl:template>

  <xsl:template match="filepath">
    <code class="filepath"><xsl:apply-templates/></code>
  </xsl:template>
  <xsl:template match="userinput">
    <kbd><xsl:apply-templates/></kbd>
  </xsl:template>
  <xsl:template match="systemoutput | msgnum">
    <samp><xsl:apply-templates/></samp>
  </xsl:template>
  <xsl:template match="varname | var">
    <var><xsl:apply-templates/></var>
  </xsl:template>
  <xsl:template match="cmdname | apiname | parmname | option">
    <code><xsl:apply-templates/></code>
  </xsl:template>

  <xsl:template match="b">
    <strong><xsl:apply-templates/></strong>
  </xsl:template>
  <xsl:template match="i">
    <em><xsl:apply-templates/></em>
  </xsl:template>
  <xsl:template match="u">
    <u><xsl:apply-templates/></u>
  </xsl:template>
  <xsl:template match="sup">
    <sup><xsl:apply-templates/></sup>
  </xsl:template>
  <xsl:template match="sub">
    <sub><xsl:apply-templates/></sub>
  </xsl:template>
  <xsl:template match="line-through">
    <del><xsl:apply-templates/></del>
  </xsl:template>
  <xsl:template match="tt">
    <code><xsl:apply-templates/></code>
  </xsl:template>

  <xsl:template match="term | keyword">
    <span class="term"><xsl:apply-templates/></span>
  </xsl:template>

  <!-- Key-referencing inline elements: resolve text through the key space
       when available; fall back to local content, then a placeholder. -->
  <xsl:template match="ph[@keyref] | keyword[@keyref] | term[@keyref] | cite[@keyref]"
                priority="1.5">
    <xsl:variable name="def" select="dbx:keydef(@keyref)"/>
    <xsl:choose>
      <xsl:when test="$def/@text">
        <span class="keyref-resolved" title="key: {@keyref}"><xsl:value-of select="$def/@text"/></span>
      </xsl:when>
      <xsl:when test="$def/@linktext">
        <span class="keyref-resolved" title="key: {@keyref}"><xsl:value-of select="$def/@linktext"/></span>
      </xsl:when>
      <xsl:when test="node()"><xsl:apply-templates/></xsl:when>
      <xsl:otherwise>
        <span class="keyref" title="key: {@keyref}"><xsl:value-of select="@keyref"/></span>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="ph | text">
    <xsl:call-template name="conref-or-content"/>
  </xsl:template>
  <xsl:template match="q">
    <q><xsl:apply-templates/></q>
  </xsl:template>
  <xsl:template match="cite">
    <cite><xsl:apply-templates/></cite>
  </xsl:template>
  <xsl:template match="tm">
    <xsl:apply-templates/><xsl:choose>
      <xsl:when test="@tmtype='reg'"><sup>&#174;</sup></xsl:when>
      <xsl:when test="@tmtype='service'"><sup>SM</sup></xsl:when>
      <xsl:otherwise><sup>&#8482;</sup></xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- ======================================================================
       Links and references
       ====================================================================== -->

  <xsl:template match="xref | link">
    <xsl:choose>
      <xsl:when test="@keyref and not(@href)">
        <xsl:variable name="def" select="dbx:keydef(@keyref)"/>
        <xsl:choose>
          <xsl:when test="$def">
            <a class="xref keyref-resolved" href="#" onclick="return false;"
               title="key {@keyref}{if ($def/@href) then concat(' → ', $def/@href) else ''}">
              <xsl:choose>
                <xsl:when test="node()"><xsl:apply-templates select="node()[not(self::desc)]"/></xsl:when>
                <xsl:when test="$def/@linktext"><xsl:value-of select="$def/@linktext"/></xsl:when>
                <xsl:when test="$def/@text"><xsl:value-of select="$def/@text"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="@keyref"/></xsl:otherwise>
              </xsl:choose>
            </a>
          </xsl:when>
          <xsl:otherwise>
            <span class="keyref" title="key: {@keyref}">
              <xsl:choose>
                <xsl:when test="node()"><xsl:apply-templates/></xsl:when>
                <xsl:otherwise><xsl:value-of select="@keyref"/></xsl:otherwise>
              </xsl:choose>
            </span>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <a class="xref" href="#" title="{@href}" onclick="return false;">
          <xsl:choose>
            <xsl:when test="node()"><xsl:apply-templates select="node()[not(self::desc)]"/></xsl:when>
            <xsl:otherwise><xsl:value-of select="@href"/></xsl:otherwise>
          </xsl:choose>
        </a>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="related-links">
    <div class="related-links">
      <p class="task-label">Related information</p>
      <ul><xsl:for-each select=".//link"><li><xsl:apply-templates select="."/></li></xsl:for-each></ul>
    </div>
  </xsl:template>

  <!-- ======================================================================
       Images
       ====================================================================== -->

  <xsl:template match="image">
    <xsl:variable name="href" select="string(@href)"/>
    <xsl:variable name="resolved">
      <xsl:choose>
        <!-- absolute URL (http:, https:, file:) — use as-is -->
        <xsl:when test="$href != '' and matches($href, '^[a-zA-Z][a-zA-Z0-9+.-]*:')"><xsl:value-of select="$href"/></xsl:when>
        <xsl:when test="$href != '' and $base-uri != ''"><xsl:value-of select="concat($base-uri, '/', $href)"/></xsl:when>
        <xsl:when test="$href != ''"><xsl:value-of select="$href"/></xsl:when>
        <!-- keyref image: the key space carries the resolved file URL
             (keydef hrefs are relative to the defining map, not this topic) -->
        <xsl:when test="@keyref"><xsl:value-of select="string(dbx:keydef(@keyref)/@fileurl)"/></xsl:when>
      </xsl:choose>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="string($resolved) = ''">
        <span class="image-unresolved"
              title="{if (@keyref) then concat('key: ', @keyref) else 'no href'}">
          <xsl:text>[image: </xsl:text>
          <xsl:value-of select="if (@keyref) then @keyref else 'missing href'"/>
          <xsl:text>]</xsl:text>
        </span>
      </xsl:when>
      <xsl:when test="@placement = 'break'">
        <div class="image-block"><img src="{$resolved}" alt="{alt}"/></div>
      </xsl:when>
      <xsl:otherwise>
        <img class="image-inline" src="{$resolved}" alt="{alt}"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="alt"/>

  <!-- ======================================================================
       Tables
       ====================================================================== -->

  <!-- CALS table -->
  <xsl:template match="table">
    <div class="table-wrap"><xsl:apply-templates/></div>
  </xsl:template>
  <xsl:template match="tgroup">
    <table><xsl:apply-templates/></table>
  </xsl:template>
  <xsl:template match="colspec"/>
  <xsl:template match="thead">
    <thead><xsl:apply-templates/></thead>
  </xsl:template>
  <xsl:template match="tbody">
    <tbody><xsl:apply-templates/></tbody>
  </xsl:template>
  <xsl:template match="row">
    <tr><xsl:apply-templates/></tr>
  </xsl:template>
  <xsl:template match="thead/row/entry">
    <th><xsl:apply-templates/></th>
  </xsl:template>
  <xsl:template match="entry">
    <td><xsl:apply-templates/></td>
  </xsl:template>

  <!-- simpletable -->
  <xsl:template match="simpletable">
    <div class="table-wrap"><table><xsl:apply-templates/></table></div>
  </xsl:template>
  <xsl:template match="sthead">
    <thead><tr><xsl:for-each select="stentry"><th><xsl:apply-templates/></th></xsl:for-each></tr></thead>
  </xsl:template>
  <xsl:template match="strow">
    <tr><xsl:apply-templates/></tr>
  </xsl:template>
  <xsl:template match="strow/stentry">
    <td><xsl:apply-templates/></td>
  </xsl:template>

  <!-- ======================================================================
       Glossary
       ====================================================================== -->

  <xsl:template match="glossterm">
    <h1 class="glossterm"><xsl:apply-templates/></h1>
  </xsl:template>
  <xsl:template match="glossdef">
    <p class="glossdef"><xsl:apply-templates/></p>
  </xsl:template>

  <!-- ======================================================================
       Map / bookmap: render as a table of contents
       ====================================================================== -->

  <xsl:template match="/map | /bookmap">
    <xsl:if test="title or booktitle">
      <h1 class="map-title">
        <xsl:value-of select="(title, booktitle/mainbooktitle, booktitle)[1]"/>
      </h1>
    </xsl:if>
    <p class="map-note">Map preview — table of contents. Use Publish for full output.</p>
    <ul class="toc">
      <xsl:apply-templates select="*" mode="toc"/>
    </ul>
  </xsl:template>

  <xsl:template match="topicref | mapref | chapter | appendix | part | topichead | topicgroup" mode="toc">
    <xsl:variable name="label">
      <xsl:choose>
        <xsl:when test="@navtitle != ''"><xsl:value-of select="@navtitle"/></xsl:when>
        <xsl:when test="topicmeta/navtitle"><xsl:value-of select="topicmeta/navtitle"/></xsl:when>
        <xsl:when test="@href != ''"><xsl:value-of select="@href"/></xsl:when>
        <xsl:when test="@keyref != ''">key: <xsl:value-of select="@keyref"/></xsl:when>
        <xsl:otherwise/>
      </xsl:choose>
    </xsl:variable>
    <li class="toc-entry">
      <xsl:choose>
        <xsl:when test="self::mapref or @format = 'ditamap' or ends-with(string(@href), '.ditamap')">
          <span class="toc-submap" title="{@href}"><xsl:value-of select="$label"/> <span class="toc-badge">submap</span></span>
        </xsl:when>
        <xsl:when test="string($label) != ''">
          <span class="toc-topic" title="{@href}"><xsl:value-of select="$label"/></span>
        </xsl:when>
        <xsl:otherwise>
          <span class="toc-group">&#8212;</span>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:if test="topicref | chapter | appendix | topichead | topicgroup | mapref">
        <ul class="toc">
          <xsl:apply-templates select="topicref | chapter | appendix | topichead | topicgroup | mapref" mode="toc"/>
        </ul>
      </xsl:if>
    </li>
  </xsl:template>

  <!-- keydefs, reltables and map metadata: skipped in the TOC -->
  <xsl:template match="keydef | reltable | topicmeta | bookmeta | frontmatter | backmatter | booktitle | title" mode="toc"/>
  <xsl:template match="*" mode="toc">
    <xsl:apply-templates select="*" mode="toc"/>
  </xsl:template>

  <!-- ======================================================================
       Conref rendering. Resolvable references are transcluded BEFORE the
       transform (ConrefTranscluder) and arrive here as ordinary elements
       tagged dbx-conref-source — block-level ones get a subtle reuse
       marker. Whatever still carries conref/conkeyref was unresolvable
       and renders as a visible badge.
       ====================================================================== -->

  <xsl:template match="*[@dbx-conref-source][not(@conref or @conkeyref)]
                        [self::note or self::p or self::section or self::fig
                         or self::table or self::simpletable or self::codeblock
                         or self::ul or self::ol or self::sl or self::dl
                         or self::example or self::pre or self::lq]" priority="3">
    <div class="conref-included" title="Reused content: {@dbx-conref-source}">
      <xsl:next-match/>
    </div>
  </xsl:template>

  <xsl:template match="*[@conref or @conkeyref]" priority="2">
    <xsl:variable name="def" select="if (@conkeyref) then dbx:keydef(@conkeyref) else ()"/>
    <xsl:variable name="label">
      <xsl:choose>
        <xsl:when test="@conref"><xsl:value-of select="@conref"/></xsl:when>
        <xsl:when test="$def/@href">
          <xsl:value-of select="concat('key ', @conkeyref, ' → ', $def/@href)"/>
        </xsl:when>
        <xsl:otherwise><xsl:value-of select="concat('key: ', @conkeyref)"/></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <span class="conref-badge" title="{$label}">
      <xsl:text>&#8618; </xsl:text>
      <xsl:value-of select="$label"/>
    </span>
    <!-- An element with conref may still carry local fallback content -->
    <xsl:apply-templates/>
  </xsl:template>

  <!-- ======================================================================
       Review proposals, shown when $show-changes: the element renders as it
       normally would (next-match), wrapped so the change is visible.
       ====================================================================== -->

  <!-- A wrapper proposal (an element put around, or taken off, text that
       stays) is not an insertion or deletion of the text: shown plain. -->
  <xsl:template match="*[$show-changes][@status = 'new'][matches(@rev, '(^|\s)(ai|user|mcp|rpc):')]
                        [not(matches(@rev, '(^|\s)review-wrapper(\s|$)'))]"
                priority="5">
    <ins class="review-new" title="{concat('Proposed by ', @rev)}"><xsl:next-match/></ins>
  </xsl:template>

  <xsl:template match="*[$show-changes][@status = 'deleted'][matches(@rev, '(^|\s)(ai|user|mcp|rpc):')]
                        [not(matches(@rev, '(^|\s)review-wrapper(\s|$)'))]"
                priority="5">
    <del class="review-deleted" title="{concat('Removed by ', @rev)}"><xsl:next-match/></del>
  </xsl:template>

  <xsl:template match="*[$show-changes][@status = 'changed'][matches(@rev, '(^|\s)(ai|user|mcp|rpc):')]"
                priority="5">
    <mark class="review-changed" title="{concat('Changed by ', @rev)}"><xsl:next-match/></mark>
  </xsl:template>

  <!-- The engine's note holding an element's previous version: not for readers -->
  <xsl:template match="draft-comment[@outputclass = 'dogsbay-previous']" priority="6"/>

  <!-- An open review comment: a review author id and no decision yet -->
  <xsl:template match="draft-comment[$show-changes][matches(@author, '^(ai|user|mcp|rpc):')]
                                    [not(@disposition) or @disposition = 'open']" priority="5">
    <span class="review-comment">
      <span class="who"><xsl:value-of select="@author"/></span>
      <xsl:apply-templates/>
    </span>
  </xsl:template>

  <!-- Any other draft comment, resolved or hand-written, is a draft note: dropped -->
  <xsl:template match="draft-comment" priority="4"/>

  <!-- ======================================================================
       Fallback: pass children through transparently so specialized or
       unknown elements never silently drop content.
       ====================================================================== -->

  <xsl:template match="*">
    <xsl:apply-templates/>
  </xsl:template>

  <!-- Copy profiling attributes through as data-* for future DITAVAL preview -->
  <xsl:template name="profiling-attrs">
    <xsl:if test="@audience"><xsl:attribute name="data-audience" select="@audience"/></xsl:if>
    <xsl:if test="@platform"><xsl:attribute name="data-platform" select="@platform"/></xsl:if>
    <xsl:if test="@product"><xsl:attribute name="data-product" select="@product"/></xsl:if>
    <xsl:if test="@otherprops"><xsl:attribute name="data-otherprops" select="@otherprops"/></xsl:if>
  </xsl:template>

  <xsl:template name="conref-or-content">
    <xsl:apply-templates/>
  </xsl:template>

</xsl:stylesheet>
