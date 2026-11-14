package africa.finishers.assets.application;

import africa.finishers.assets.domain.GlbContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Safe-processing checks for an uploaded GLB (Appendix F3). It refuses anything the storefront cannot
 * render without extra decoders, and anything that would make the browser fetch another file.
 * This is a gate, not a substitute for the Khronos glTF validator (AT-3D-02), which reviewers still run.
 */
public final class GlbInspector {
    public record Report(int meshes,int materials,int textures,int images,int nodes,long binaryBytes,List<String> extensions,List<String> warnings) {}

    /** Extensions three.js GLTFLoader renders without an extra decoder that this project does not ship. */
    static final Set<String> SUPPORTED=Set.of("KHR_materials_emissive_strength","KHR_materials_clearcoat","KHR_materials_ior",
        "KHR_materials_sheen","KHR_materials_specular","KHR_materials_transmission","KHR_materials_unlit","KHR_materials_volume",
        "KHR_texture_transform","KHR_mesh_quantization","KHR_materials_iridescence","KHR_materials_anisotropy");
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final int MAX_ITEMS=2000;
    private GlbInspector(){}

    public static Report inspect(byte[] file){
        var container=GlbContainer.read(file);
        JsonNode root;
        try{root=JSON.readTree(container.json());}catch(RuntimeException e){throw new GlbContainer.Invalid("The GLB JSON chunk is not valid JSON.");}
        if(root==null||!root.isObject())throw new GlbContainer.Invalid("The GLB JSON chunk must be a JSON object.");
        String version=root.path("asset").path("version").asString("");
        if(!version.startsWith("2."))throw new GlbContainer.Invalid("The model must declare glTF asset version 2.x.");
        var buffers=list(root,"buffers");
        for(int i=0;i<buffers.size();i++){
            var buffer=buffers.get(i);
            if(buffer.has("uri"))throw new GlbContainer.Invalid("Buffer "+i+" points to another file. Export a self-contained GLB with all data embedded.");
            if(i>0)throw new GlbContainer.Invalid("A GLB file may embed only one binary buffer.");
            long declared=buffer.path("byteLength").asLong(-1);
            if(declared<0||declared>container.binaryBytes())throw new GlbContainer.Invalid("The embedded buffer is larger than the GLB binary chunk.");
        }
        var images=list(root,"images");
        for(int i=0;i<images.size();i++)if(images.get(i).has("uri"))throw new GlbContainer.Invalid("Image "+i+" points to another file. Embed textures in the GLB.");
        var meshes=list(root,"meshes");
        if(meshes.isEmpty())throw new GlbContainer.Invalid("The model contains no meshes.");
        var extensions=new TreeSet<String>();
        list(root,"extensionsUsed").forEach(name->extensions.add(name.asString("")));
        var unsupported=new ArrayList<String>();
        list(root,"extensionsRequired").forEach(name->{if(!SUPPORTED.contains(name.asString("")))unsupported.add(name.asString(""));});
        if(!unsupported.isEmpty())throw new GlbContainer.Invalid("The model requires unsupported extensions: "+String.join(", ",unsupported)+". Export without Draco, Meshopt or Basis compression.");
        var warnings=new ArrayList<String>();
        for(String name:extensions)if(!SUPPORTED.contains(name))warnings.add("Uses optional extension "+name+"; it may be ignored by the storefront.");
        if(list(root,"animations").size()>0)warnings.add("Contains animations; the storefront shows the model still.");
        if(list(root,"cameras").size()>0)warnings.add("Contains cameras; they are ignored.");
        return new Report(meshes.size(),list(root,"materials").size(),list(root,"textures").size(),images.size(),list(root,"nodes").size(),container.binaryBytes(),List.copyOf(extensions),List.copyOf(warnings));
    }

    private static List<JsonNode> list(JsonNode root,String field){
        var node=root.get(field);
        if(node==null||node.isNull())return List.of();
        if(!node.isArray())throw new GlbContainer.Invalid("The glTF field '"+field+"' must be a list.");
        if(node.size()>MAX_ITEMS)throw new GlbContainer.Invalid("The model has too many "+field+" (more than "+MAX_ITEMS+").");
        var items=new ArrayList<JsonNode>();node.forEach(items::add);return items;
    }
}
