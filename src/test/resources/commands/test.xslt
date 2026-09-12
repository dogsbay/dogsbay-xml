<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html" indent="yes"/>
    <xsl:template match="/root">
        <html>
            <body>
                <h1><xsl:value-of select="title"/></h1>
                <xsl:for-each select="item">
                    <p><xsl:value-of select="."/></p>
                </xsl:for-each>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
