package me.vilsol.blockly2java;

import me.vilsol.blockly2java.annotations.BBlock;
import me.vilsol.blockly2java.annotations.BField;
import me.vilsol.blockly2java.annotations.BStatement;
import me.vilsol.blockly2java.annotations.BValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Blockly2Java {

    private static HashMap<String, BlocklyBlock> blocks = new HashMap<>();
    private static Pattern nodePattern = Pattern.compile("(<.*?>)([\\w\\d\\s.]*)");
    private static Pattern attributePattern = Pattern.compile("([a-zA-Z0-9]+)=\"(.+?)\"");

    public static void registerClass(Class<?> block){
        BBlock b = block.getAnnotation(BBlock.class);
        if(b == null){
            throw new RuntimeException("Tried to register block (" + block.getName() + ") without @BBlock annotation");
        }

        HashMap<String, Field> blockFields = new HashMap<>();
        HashMap<String, Field> blockValues = new HashMap<>();
        HashMap<String, Field> blockStatements = new HashMap<>();

        Field[] fields = block.getDeclaredFields();
        for(Field field : fields){
            Annotation f;
            if((f = field.getAnnotation(BField.class)) != null){
                blockFields.put(((BField) f).value(), field);
            }else if((f = field.getAnnotation(BValue.class)) != null){
                if(field.getDeclaringClass().getAnnotation(BBlock.class) == null){
                    throw new RuntimeException("Class:" + block.getName() + " Field:" + field.getName() + " Should be a @BBlock class!");
                }
                blockValues.put(((BValue) f).value(), field);
            }else if((f = field.getAnnotation(BStatement.class)) != null){
                if(!field.getType().isAssignableFrom(List.class)){
                    throw new RuntimeException("Class:" + block.getName() + " Field:" + field.getName() + " Should be a list!");
                }
                blockStatements.put(((BStatement) f).value(), field);
            }
        }

        blocks.put(b.value(), new BlocklyBlock(block, b.value(), blockFields, blockValues, blockStatements));
    }

    public static Object parseBlockly(String blockly){
        Matcher m = nodePattern.matcher(blockly);
        Stack<Node> nodes = new Stack<>();
        Node lastNode = null;
        int ignoreBlocks = 0;
        while(m.find()){
            if(!m.group(1).startsWith("</")) {
                Node node = getNode(m.group(1), m.group(2));
                if(node.getName().equals("next")){
                    nodes.pop();
                    lastNode = nodes.peek();
                    ignoreBlocks++;
                    continue;
                }

                if (lastNode != null) {
                    lastNode.addSubnode(node);
                }

                nodes.add(node);
                lastNode = node;
            }else{
                if(nodes.size() > 0) {
                    if (nodes.peek().getName().equals("block") && ignoreBlocks > 0) {
                        ignoreBlocks--;
                    } else if (!m.group(1).contains("next")) {
                        nodes.pop();
                        if (nodes.size() > 0) {
                            lastNode = nodes.peek();
                        }
                    }
                }
            }
        }

        assert lastNode != null;
        return parseBock(lastNode);
    }

    private static Object parseBock(Node node){
        BlocklyBlock baseBlock = blocks.get(node.getAttributes().get("type"));
        if(baseBlock == null){
            throw new RuntimeException("No block with type '" + node.getAttributes().get("type") + "' registered!");
        }

        Object base = baseBlock.newInstance();
        fillBlock(baseBlock, base, node);
        return base;
    }

    private static void fillBlock(BlocklyBlock block, Object base, Node node){
        if(node.getSubnodes() == null || node.getSubnodes().size() == 0){
            return;
        }

        for(Node s : node.getSubnodes()){
            switch(s.getName()){
                case "field":
                    Field field = block.getFields().get(s.getAttributes().get("name"));
                    if(field.getType().equals(int.class) || field.getType().equals(Integer.class)){
                        setValue(base, field, Integer.parseInt(s.getValue()));
                    }else if(field.getType().equals(double.class) || field.getType().equals(Double.class)) {
                        setValue(base, field, Double.parseDouble(s.getValue()));
                    }else if(field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                        setValue(base, field, Float.parseFloat(s.getValue()));
                    }else if(field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                        setValue(base, field, Boolean.parseBoolean(s.getValue()));
                    }else{
                        setValue(base, field, s.getValue());
                    }
                    break;
                case "value":
                    setValue(base, block.getValues().get(s.getAttributes().get("name")), parseBock(s.getSubnodes().iterator().next()));
                    break;
                case "statement":
                    List<Object> objects = new ArrayList<>();
                    if(s.getSubnodes() != null && s.getSubnodes().size() > 0){
                        for(Node n : s.getSubnodes()){
                            objects.add(parseBock(n));
                        }
                    }
                    setValue(base, block.getStatements().get(s.getAttributes().get("name")), objects);
                    break;
            }
        }
    }

    private static void setValue(Object object, Field field, Object value){
        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static Node getNode(String node, String value){
        String name = node.split("\\s")[0].split(">")[0].substring(1);
        Matcher m = attributePattern.matcher(node);
        HashMap<String, String> attributes = new HashMap<>();
        while(m.find()){
            attributes.put(m.group(1), m.group(2));
        }
        return new Node(name, attributes, value);
    }

}