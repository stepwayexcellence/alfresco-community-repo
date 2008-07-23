<#assign username=userId>
<#if firstName?exists>
   <#assign username = firstName + " " + lastName>
</#if>
<item>
    <title>New blog post: ${(postTitle!"unknown")?xml}</title>
    <link>${(browsePostUrl!'')?xml}</link>
    <guid>${id}</guid>
    <description>${username?xml} added blog post ${(postTitle!'unknown')?xml}.</description>
</item>

